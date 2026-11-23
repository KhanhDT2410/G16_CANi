package com.cani.service;

import com.cani.client.ekyc.EkycProvider;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EkycUserSubmittedInput;
import com.cani.dto.ekyc.EkycVerificationResultDto;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerEkycVerification;
import com.cani.entity.enums.EkycRoutingDecision;
import com.cani.entity.enums.EkycVerificationStatus;
import com.cani.entity.enums.KycStatus;
import com.cani.entity.enums.UserRole;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.UserRepository;
import com.cani.repository.WorkerEkycVerificationRepository;
import com.cani.repository.WorkerRepository;
import com.cani.service.ekyc.EkycCccdLockManager;
import com.cani.service.ekyc.EkycDecisionEngine;
import com.cani.service.ekyc.EkycIdentityCryptoService;
import com.cani.service.ekyc.storage.EkycImageAccessTokenService;
import com.cani.service.ekyc.storage.EkycImageRetentionService;
import com.cani.service.ekyc.storage.EkycImageStorage;
import com.cani.service.ekyc.storage.EkycImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service quản lý luồng định danh eKYC cho Worker thông qua VNPT eKYC thực tế.
 *
 * Nguyên tắc bảo mật & toàn vẹn dữ liệu:
 * - Không giữ transaction DB trong lúc gọi HTTP sang VNPT eKYC.
 * - Tuyệt đối không cho dữ liệu QR/nhập tay từ client ghi đè kết quả OCR của VNPT.
 * - Tách bạch rõ lỗi kỹ thuật (TECHNICAL_FAILURE - cho phép retry) khỏi lỗi nghiệp vụ (REJECTED)
 *   và hồ sơ cần kiểm tra (PENDING_REVIEW).
 * - Lưu ảnh CCCD/Selfie vào private storage, DB chỉ lưu objectKey nội bộ; cấp signed URL ngắn hạn khi trả API.
 * - Không hạ trạng thái tài khoản đã APPROVED khi lần xác minh mới gặp lỗi kỹ thuật.
 * - Retry cập nhật trực tiếp bản ghi TECHNICAL_FAILURE/PENDING gần nhất để tránh sinh bản ghi rác.
 * - Không ghi CCCD đầy đủ, họ tên hay ảnh vào log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerEkycService {

    private static final List<String> ACTIVE_DUPLICATE_STATUSES = List.of(
            EkycVerificationStatus.APPROVED.name(),
            EkycVerificationStatus.PENDING_REVIEW.name()
    );

    private static final List<String> RETRYABLE_RECORD_STATUSES = List.of(
            EkycVerificationStatus.TECHNICAL_FAILURE.name(),
            EkycVerificationStatus.PENDING.name(),
            EkycVerificationStatus.PROCESSING.name()
    );

    private final WorkerRepository workerRepository;
    private final WorkerEkycVerificationRepository ekycVerificationRepository;
    private final UserRepository userRepository;
    private final EkycProvider ekycProvider;
    private final EkycDecisionEngine decisionEngine;
    private final EkycIdentityCryptoService cryptoService;
    private final EkycImageValidator imageValidator;
    private final EkycImageStorage imageStorage;
    private final EkycImageAccessTokenService imageAccessTokenService;
    private final EkycImageRetentionService imageRetentionService;
    private final EkycCccdLockManager cccdLockManager;

    @Autowired(required = false)
    private EoEkycVerificationRepository eoEkycVerificationRepository;

    @Autowired(required = false)
    private TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    @Value("${cani.ekyc.strict:false}")
    private boolean ekycStrict;

    public EkycVerificationResultDto submitEkyc(
            UUID userId,
            MultipartFile frontFile,
            MultipartFile backFile,
            MultipartFile selfieFile) {
        return submitEkyc(userId, frontFile, backFile, selfieFile, null, null, null, null, null, null, null, null);
    }

    /**
     * Worker nộp hồ sơ xác thực eKYC (Mặt trước, Mặt sau, Selfie).
     * Các tham số confirmed* từ QR/nhập tay chỉ dùng để đối chiếu chéo — KHÔNG ghi đè kết quả VNPT OCR.
     * Phương thức ngoài chủ ý KHÔNG gắn @Transactional để tránh giữ kết nối DB trong suốt lúc gọi VNPT API.
     */
    public EkycVerificationResultDto submitEkyc(
            UUID userId,
            MultipartFile frontFile,
            MultipartFile backFile,
            MultipartFile selfieFile,
            String confirmedCccd,
            String confirmedFullName,
            String confirmedDob,
            String confirmedGender,
            String confirmedOriginAddress,
            String confirmedResidenceAddress,
            String confirmedIssueDate,
            String confirmedExpiryDate) {

        // 1. Xác thực tệp ảnh bằng magic bytes, MIME type và dung lượng tối đa
        cryptoService.requireIndexKey();
        EkycImageValidator.ValidatedEkycImage validatedFront = imageValidator.validate(frontFile, "Ảnh mặt trước CCCD");
        EkycImageValidator.ValidatedEkycImage validatedBack = imageValidator.validate(backFile, "Ảnh mặt sau CCCD");
        EkycImageValidator.ValidatedEkycImage validatedSelfie = imageValidator.validate(selfieFile, "Ảnh chân dung Selfie");

        EkycUserSubmittedInput userInput = EkycUserSubmittedInput.builder()
                .cccdNumber(confirmedCccd)
                .fullName(confirmedFullName)
                .dob(confirmedDob)
                .gender(confirmedGender)
                .originAddress(confirmedOriginAddress)
                .residenceAddress(confirmedResidenceAddress)
                .issueDate(confirmedIssueDate)
                .expiryDate(confirmedExpiryDate)
                .build();

        // 2. Transaction ngắn 1: Chuẩn bị Worker & bản ghi PROCESSING (tái sử dụng bản ghi lỗi kỹ thuật khi retry)
        PreparedSubmission prepared = executeInTransaction(() -> prepareSubmissionRecord(userId));

        // 3. Lưu ảnh vào Private Object Storage (fail-fast, tuyệt đối không tạo URL giả)
        EkycImageStorage.StoredObject storedFront = null;
        EkycImageStorage.StoredObject storedBack = null;
        EkycImageStorage.StoredObject storedSelfie = null;
        try {
            storedFront = imageStorage.store("worker", prepared.workerId(), "front",
                    validatedFront.getBytes(), validatedFront.getSafeExtension(), validatedFront.getDetectedMimeType());
            storedBack = imageStorage.store("worker", prepared.workerId(), "back",
                    validatedBack.getBytes(), validatedBack.getSafeExtension(), validatedBack.getDetectedMimeType());
            storedSelfie = imageStorage.store("worker", prepared.workerId(), "selfie",
                    validatedSelfie.getBytes(), validatedSelfie.getSafeExtension(), validatedSelfie.getDetectedMimeType());
        } catch (RuntimeException storageEx) {
            imageRetentionService.deleteStoredImagesQuietly(
                    storedFront != null ? storedFront.getObjectKey() : null,
                    storedBack != null ? storedBack.getObjectKey() : null,
                    storedSelfie != null ? storedSelfie.getObjectKey() : null
            );
            executeInTransaction(() -> markTechnicalFailure(prepared.verificationId(), prepared.attempt(), "STORAGE_WRITE_FAILED"));
            throw storageEx;
        }

        boolean finalizationStarted = false;
        try {
            // 4. Gọi VNPT eKYC thực tế (bên ngoài DB transaction)
            EkycIdentityResultDto providerResult = ekycProvider.verifyIdentity(
                    validatedFront.getBytes(), validatedFront.getDetectedMimeType(),
                    validatedBack.getBytes(), validatedBack.getDetectedMimeType(),
                    validatedSelfie.getBytes(), validatedSelfie.getDetectedMimeType()
            );

            String verifiedRawCccd = (providerResult != null && !providerResult.isTechnicalFailure())
                    ? cryptoService.normalizeCccd(providerResult.getIdNumber())
                    : null;
            String primaryHash = cryptoService.hashCccd(verifiedRawCccd);
            Set<String> lookupHashes = cryptoService.computeLookupHashes(verifiedRawCccd);

            final EkycImageStorage.StoredObject finalFront = storedFront;
            final EkycImageStorage.StoredObject finalBack = storedBack;
            final EkycImageStorage.StoredObject finalSelfie = storedSelfie;

            // 5. Transaction ngắn 2 (đồng bộ khóa theo hash CCCD): Check trùng, chấm quyết định & lưu kết quả
            finalizationStarted = true;
            EkycVerificationResultDto result = cccdLockManager.executeWithCccdLock(primaryHash, () ->
                    executeInTransaction(() -> {
                        imageRetentionService.deleteAfterRollback(finalFront.getObjectKey(),
                                finalBack.getObjectKey(), finalSelfie.getObjectKey());
                        return finalizeSubmission(
                            userId,
                            prepared.workerId(),
                            prepared.verificationId(),
                            prepared.attempt(),
                            providerResult,
                            userInput,
                            verifiedRawCccd,
                            primaryHash,
                            lookupHashes,
                            finalFront.getObjectKey(),
                            finalBack.getObjectKey(),
                            finalSelfie.getObjectKey());
                    })
            );
            if (prepared.isRetry()) {
                imageRetentionService.deleteStoredImagesQuietly(
                        prepared.previousFrontKey(),
                        prepared.previousBackKey(),
                        prepared.previousSelfieKey()
                );
            }
            return result;
        } catch (RuntimeException failure) {
            if (!finalizationStarted) {
                imageRetentionService.deleteStoredImagesQuietly(storedFront.getObjectKey(),
                        storedBack.getObjectKey(), storedSelfie.getObjectKey());
            }
            try {
                executeInTransaction(() -> markTechnicalFailure(prepared.verificationId(), prepared.attempt(), "FINALIZATION_FAILED"));
            } catch (RuntimeException statusUpdateFailure) {
                failure.addSuppressed(statusUpdateFailure);
            }
            throw failure;
        }
    }

    private PreparedSubmission prepareSubmissionRecord(UUID userId) {
        userRepository.lockForEkyc(userId);
        Worker worker = getOrCreateWorker(userId);

        Optional<WorkerEkycVerification> retryCandidate = ekycVerificationRepository
                .findTopByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId()).filter(row -> RETRYABLE_RECORD_STATUSES.contains(row.getStatus()));

        WorkerEkycVerification record;
        boolean isRetry = false;
        String prevFront = null;
        String prevBack = null;
        String prevSelfie = null;

        if (retryCandidate.filter(candidate -> !candidate.isLegalHold()).isPresent()) {
            record = retryCandidate.get();
            isRetry = true;
            prevFront = record.getFrontImageUrl();
            prevBack = record.getBackImageUrl();
            prevSelfie = record.getSelfieImageUrl();
            int nextRetry = (record.getRetryCount() != null ? record.getRetryCount() : 0) + 1;
            record.setRetryCount(nextRetry);
            record.setStatus(EkycVerificationStatus.PROCESSING.name());
            record.setRoutingDecision(EkycRoutingDecision.MANUAL_REVIEW.name());
            record.setRoutingReason("Đang thử lại xác minh hồ sơ với VNPT eKYC...");
            record.setFailureCode(null);
            record.setProvider(ekycProvider.getProviderName());
        } else {
            record = WorkerEkycVerification.builder()
                    .worker(worker)
                    .provider(ekycProvider.getProviderName())
                    .status(EkycVerificationStatus.PROCESSING.name())
                    .routingDecision(EkycRoutingDecision.MANUAL_REVIEW.name())
                    .routingReason("Đang gửi hồ sơ xác minh tới VNPT eKYC...")
                    .retryCount(0)
                    .build();
        }

        record = ekycVerificationRepository.save(record);
        return new PreparedSubmission(worker.getWorkerId(), record.getId(), record.getRetryCount(), isRetry, prevFront, prevBack, prevSelfie);
    }

    private void markTechnicalFailure(UUID verificationId, Integer attempt, String code) {
        if (verificationId == null) {
            return;
        }
        ekycVerificationRepository.findById(verificationId)
                .filter(record -> !record.isLegalHold()
                        && Objects.equals(record.getRetryCount(), attempt)
                        && EkycVerificationStatus.PROCESSING.name().equals(record.getStatus()))
                .ifPresent(record -> {
            record.setStatus(EkycVerificationStatus.TECHNICAL_FAILURE.name());
            record.setRoutingDecision(EkycRoutingDecision.TECHNICAL_FAILURE.name());
            record.setFailureCode(code);
            record.setImageRetentionUntil(imageRetentionService.calculateRetentionDeadline(EkycVerificationStatus.TECHNICAL_FAILURE));
            record.setRoutingReason("Không thể hoàn tất lưu hồ sơ. Vui lòng thử lại sau.");
            ekycVerificationRepository.save(record);
        });
    }

    private EkycVerificationResultDto finalizeSubmission(
            UUID userId,
            UUID workerId,
            UUID verificationId,
            int attempt,
            EkycIdentityResultDto providerResult,
            EkycUserSubmittedInput userInput,
            String verifiedRawCccd,
            String primaryHash,
            Set<String> lookupHashes,
            String frontObjectKey,
            String backObjectKey,
            String selfieObjectKey) {

        userRepository.lockForEkyc(userId);
        cccdLockManager.acquireDbTransactionLockIfPostgres(primaryHash);

        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ Worker."));

        WorkerEkycVerification verification = ekycVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new BadRequestException("Hồ sơ xác minh đã thay đổi."));
        if (verification.isLegalHold() || !Objects.equals(verification.getRetryCount(), attempt)
                || !EkycVerificationStatus.PROCESSING.name().equals(verification.getStatus())) {
            throw new BadRequestException("Hồ sơ đã được thay thế hoặc bảo lưu.");
        }

        boolean isDuplicate = false;
        if (providerResult != null && !providerResult.isTechnicalFailure() && cryptoService.isValidCccdFormat(verifiedRawCccd)) {
            isDuplicate = isCccdDuplicateForWorker(workerId, userId, verifiedRawCccd, lookupHashes);
        }

        EkycDecisionEngine.DecisionOutcome outcome = decisionEngine.evaluate(
                providerResult,
                userInput,
                isDuplicate,
                ekycStrict
        );

        verification.setProvider(providerResult != null && providerResult.getProviderName() != null
                ? providerResult.getProviderName()
                : ekycProvider.getProviderName());
        verification.setProviderReference(providerResult != null ? providerResult.getProviderReference() : null);
        verification.setFrontImageUrl(frontObjectKey);
        verification.setBackImageUrl(backObjectKey);
        verification.setSelfieImageUrl(selfieObjectKey);
        verification.setImagesPurgedAt(null);
        verification.setImageRetentionUntil(imageRetentionService.calculateRetentionDeadline(outcome.getStatus()));
        verification.setFraudFlags(outcome.getFraudFlags());
        verification.setStatus(outcome.getStatus().name());
        verification.setRoutingDecision(outcome.getRoutingDecision().name());
        verification.setRoutingReason(outcome.getRoutingReason());
        verification.setFailureCode(outcome.getFailureCode());

        String maskedNumber = cryptoService.maskCccd(verifiedRawCccd);
        if (providerResult != null && !providerResult.isTechnicalFailure()) {
            verification.setCccdNumberHash(primaryHash);
            verification.setCccdNumberMasked(maskedNumber);
            verification.setFullName(providerResult.getFullName());
            verification.setDob(providerResult.getDob());
            verification.setGender(providerResult.getGender());
            if (providerResult.getNationality() != null && !providerResult.getNationality().isBlank()) {
                verification.setNationality(providerResult.getNationality());
            }
            verification.setOriginAddress(providerResult.getOriginAddress());
            verification.setResidenceAddress(providerResult.getResidenceAddress());
            verification.setExpiryDate(providerResult.getExpiryDate());
            verification.setIssueDate(providerResult.getIssueDate());
            verification.setIdentificationFeatures(null);
            verification.setFaceMatchScore(scaleOrNull(providerResult.getFaceMatchScore()));
            verification.setOcrConfidence(scaleOrNull(providerResult.getOcrConfidence()));
        } else {
            verification.setFaceMatchScore(null);
            verification.setOcrConfidence(null);
        }

        verification = ekycVerificationRepository.save(verification);

        // Đồng bộ trạng thái sang bảng Worker — TUYỆT ĐỐI không hạ cấp tài khoản đã APPROVED
        applyWorkerAccountStatus(worker, outcome.getStatus(), maskedNumber, providerResult);
        workerRepository.save(worker);

        log.info("Worker eKYC completed [workerId={}, verificationId={}, status={}, decision={}, failureCode={}, retryCount={}]",
                worker.getWorkerId(), verification.getId(), verification.getStatus(),
                verification.getRoutingDecision(), verification.getFailureCode(), verification.getRetryCount());

        recordAudit(userId, "WORKER_EKYC_SUBMITTED", verification.getId(),
                Map.of("status", verification.getStatus(),
                        "decision", verification.getRoutingDecision(),
                        "retryCount", verification.getRetryCount() != null ? verification.getRetryCount() : 0));

        return mapToDto(verification);
    }

    private void applyWorkerAccountStatus(
            Worker worker,
            EkycVerificationStatus verificationStatus,
            String maskedNumber,
            EkycIdentityResultDto providerResult) {

        boolean alreadyApproved = worker.getKycStatus() == KycStatus.APPROVED;

        switch (verificationStatus) {
            case APPROVED -> {
                worker.setKycStatus(KycStatus.APPROVED);
                if (maskedNumber != null) {
                    worker.setCccdNumber(maskedNumber);
                }
                if (providerResult != null && providerResult.getFullName() != null && !providerResult.getFullName().isBlank()) {
                    String verifiedName = providerResult.getFullName().trim();
                    worker.setFullName(verifiedName);
                    if (worker.getUser() != null) {
                        worker.getUser().setFullName(verifiedName);
                    }
                }
            }
            case REJECTED -> {
                if (!alreadyApproved) {
                    worker.setKycStatus(KycStatus.REJECTED);
                }
            }
            case PENDING, PROCESSING, PENDING_REVIEW -> {
                if (!alreadyApproved) {
                    worker.setKycStatus(KycStatus.PENDING);
                }
            }
            case TECHNICAL_FAILURE -> {
                // Không hạ trạng thái đã APPROVED và không chuyển sang REJECTED khi chỉ là lỗi kỹ thuật
                if (worker.getKycStatus() == null) {
                    worker.setKycStatus(KycStatus.PENDING);
                }
            }
        }
    }

    private boolean isCccdDuplicateForWorker(UUID workerId, UUID userId, String rawCccd, Set<String> lookupHashes) {
        if (lookupHashes != null && !lookupHashes.isEmpty()) {
            if (ekycVerificationRepository.existsByCccdNumberHashInAndStatusInAndWorker_WorkerIdNot(
                    lookupHashes, ACTIVE_DUPLICATE_STATUSES, workerId)) {
                return true;
            }
            if (eoEkycVerificationRepository != null && userId != null
                    && eoEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndEventOrganizer_User_UserIdNot(
                            lookupHashes, ACTIVE_DUPLICATE_STATUSES, userId)) {
                return true;
            }
        }
        Set<String> legacyCandidates = cryptoService.buildLegacyCandidateStrings(rawCccd);
        if (!legacyCandidates.isEmpty()) {
            return workerRepository.existsByCccdNumberInAndWorkerIdNot(legacyCandidates, workerId);
        }
        return false;
    }

    private Worker getOrCreateWorker(UUID userId) {
        return workerRepository.findByUser_UserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy User cho User ID: " + userId));
                    if (user.getRole() != null && user.getRole() != UserRole.WORKER && user.getRole() != UserRole.ADMIN) {
                        throw new BadRequestException("Tài khoản không phải là Worker");
                    }
                    String defaultName = (user.getFullName() != null && !user.getFullName().isBlank())
                            ? user.getFullName().trim()
                            : "Worker";
                    return workerRepository.save(Worker.builder()
                            .user(user)
                            .fullName(defaultName)
                            .kycStatus(KycStatus.PENDING)
                            .isOnline(false)
                            .build());
                });
    }

    /**
     * Lấy kết quả thẩm định eKYC gần nhất của Worker
     */
    @Transactional(readOnly = true)
    public EkycVerificationResultDto getMyEkycStatus(UUID userId) {
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ Worker"));

        return ekycVerificationRepository.findTopByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId())
                .map(this::mapToDto)
                .orElse(null);
    }

    /**
     * Admin / Customer Care duyệt thủ công hồ sơ eKYC (Human-in-the-loop)
     */
    @Transactional
    public EkycVerificationResultDto adminReviewEkyc(UUID adminUserId, UUID verificationId, EkycAdminReviewRequestDto dto) {
        WorkerEkycVerification verification = ekycVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ eKYC: " + verificationId));

        if (!EkycVerificationStatus.PENDING_REVIEW.name().equals(verification.getStatus())) {
            throw new BadRequestException("Chỉ được thẩm định hồ sơ đang ở trạng thái PENDING_REVIEW (hiện tại: " + verification.getStatus() + ")");
        }

        String decision = dto.getDecision().toUpperCase().trim();
        if (!EkycVerificationStatus.APPROVED.name().equals(decision)
                && !EkycVerificationStatus.REJECTED.name().equals(decision)) {
            throw new BadRequestException("Quyết định duyệt chỉ chấp nhận APPROVED hoặc REJECTED");
        }

        Worker worker = verification.getWorker();
        if (EkycVerificationStatus.APPROVED.name().equals(decision)) {
            if (verification.getCccdNumberHash() == null || worker == null) {
                throw new BadRequestException("Hồ sơ thiếu số giấy tờ đã đối chiếu. Vui lòng yêu cầu nộp lại hồ sơ.");
            }
            cccdLockManager.acquireDbTransactionLockIfPostgres(verification.getCccdNumberHash());
            boolean isDup = ekycVerificationRepository.existsByCccdNumberHashAndStatusAndWorker_WorkerIdNot(
                    verification.getCccdNumberHash(), EkycVerificationStatus.APPROVED.name(), worker.getWorkerId());
            if (!isDup && eoEkycVerificationRepository != null && worker.getUser() != null) {
                isDup = eoEkycVerificationRepository.existsByCccdNumberHashAndStatusInAndEventOrganizer_User_UserIdNot(
                        verification.getCccdNumberHash(), List.of(EkycVerificationStatus.APPROVED.name()), worker.getUser().getUserId());
            }
            if (isDup) {
                throw new BadRequestException("Số CCCD này đã được phê duyệt cho một tài khoản khác trong hệ thống! Mỗi CCCD chỉ được định danh cho duy nhất 1 tài khoản.");
            }
        }

        EkycVerificationStatus targetStatus = EkycVerificationStatus.valueOf(decision);
        verification.setStatus(targetStatus.name());
        verification.setRoutingDecision(targetStatus == EkycVerificationStatus.APPROVED
                ? EkycRoutingDecision.MANUAL_APPROVED.name()
                : EkycRoutingDecision.MANUAL_REJECTED.name());
        verification.setReviewedAt(java.time.Instant.now());
        verification.setReviewedBy(adminUserId);
        verification.setReviewNotes(dto.getReviewNotes());
        verification.setImageRetentionUntil(imageRetentionService.calculateRetentionDeadline(targetStatus));
        verification = ekycVerificationRepository.save(verification);

        if (worker != null) {
            if (targetStatus == EkycVerificationStatus.APPROVED) {
                worker.setKycStatus(KycStatus.APPROVED);
                if (verification.getFullName() != null && !verification.getFullName().isBlank()) {
                    worker.setFullName(verification.getFullName().trim());
                    if (worker.getUser() != null) worker.getUser().setFullName(worker.getFullName());
                }
                if (verification.getCccdNumberMasked() != null) {
                    worker.setCccdNumber(verification.getCccdNumberMasked());
                }
            } else if (worker.getKycStatus() != KycStatus.APPROVED) {
                worker.setKycStatus(KycStatus.REJECTED);
            }
            workerRepository.save(worker);
        }

        log.info("Admin {} reviewed Worker eKYC [verificationId={}, decision={}]", adminUserId, verificationId, decision);
        recordAudit(adminUserId, "WORKER_EKYC_ADMIN_REVIEWED", verificationId,
                Map.of("status", decision));
        return EkycVerificationResultDto.builder()
                .verificationId(verificationId)
                .status(verification.getStatus())
                .routingDecision(verification.getRoutingDecision())
                .reviewedBy(adminUserId)
                .reviewedAt(verification.getReviewedAt())
                .build();
    }

    /**
     * Reset hồ sơ eKYC của Worker về trạng thái chưa định danh (xóa kèm ảnh riêng tư trong kho lưu trữ).
     */
    @Transactional
    public void resetMyEkyc(UUID userId) {
        userRepository.lockForEkyc(userId);
        Worker worker = workerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker không tồn tại"));

        List<WorkerEkycVerification> oldList = ekycVerificationRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(worker.getWorkerId());
        if (oldList != null && !oldList.isEmpty()) {
            for (WorkerEkycVerification old : oldList) {
                if (old.isLegalHold()) throw new BadRequestException("Hồ sơ đang được bảo lưu.");
                imageRetentionService.deleteAfterCommit(
                        old.getFrontImageUrl(), old.getBackImageUrl(), old.getSelfieImageUrl());
            }
            ekycVerificationRepository.deleteAll(oldList);
        }

        worker.setKycStatus(KycStatus.PENDING);
        worker.setCccdNumber(null);
        workerRepository.save(worker);
        log.info("Reset eKYC completed [workerId={}]", worker.getWorkerId());
    }

    @Transactional
    public EkycVerificationResultDto updateLegalHold(UUID adminUserId, UUID verificationId, boolean legalHold, String reason) {
        WorkerEkycVerification verification = ekycVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ eKYC: " + verificationId));
        verification.setLegalHold(legalHold);
        verification = ekycVerificationRepository.save(verification);
        recordAudit(adminUserId, "WORKER_EKYC_LEGAL_HOLD_UPDATED", verificationId,
                Map.of("legalHold", legalHold, "reason", reason != null ? reason.trim() : ""));
        log.info("Admin {} updated Worker eKYC legalHold={} [verificationId={}]", adminUserId, legalHold, verificationId);
        return mapToDto(verification, false);
    }

    /**
     * Danh sách hàng đợi hồ sơ eKYC chờ Admin duyệt
     */
    @Transactional(readOnly = true)
    public List<EkycVerificationResultDto> getPendingEkycQueue() {
        return ekycVerificationRepository.findByStatusOrderByCreatedAtDesc(EkycVerificationStatus.PENDING_REVIEW.name()).stream()
                .map(row -> {
                    EkycVerificationResultDto dto = mapToDto(row, false);
                    dto.setFrontImageUrl(null);
                    dto.setBackImageUrl(null);
                    dto.setSelfieImageUrl(null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EkycVerificationResultDto getPendingReviewDetails(UUID verificationId) {
        WorkerEkycVerification record = ekycVerificationRepository.findById(verificationId)
                .filter(row -> EkycVerificationStatus.PENDING_REVIEW.name().equals(row.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ eKYC đang chờ duyệt."));
        return mapToDto(record, false);
    }

    private EkycVerificationResultDto mapToDto(WorkerEkycVerification v) {
        return mapToDto(v, true);
    }

    private EkycVerificationResultDto mapToDto(WorkerEkycVerification v, boolean signedLinks) {
        boolean hasManagedImages = v.getImagesPurgedAt() == null
                && (imageStorage.isManagedObjectKey(v.getFrontImageUrl())
                || imageStorage.isManagedObjectKey(v.getBackImageUrl())
                || imageStorage.isManagedObjectKey(v.getSelfieImageUrl()));

        String signedFront = resolveSignedImageUrl(v, v.getFrontImageUrl(), "front", signedLinks);
        String signedBack = resolveSignedImageUrl(v, v.getBackImageUrl(), "back", signedLinks);
        String signedSelfie = resolveSignedImageUrl(v, v.getSelfieImageUrl(), "selfie", signedLinks);

        return EkycVerificationResultDto.builder()
                .verificationId(v.getId())
                .workerId(v.getWorker() != null ? v.getWorker().getWorkerId() : null)
                .workerName(v.getWorker() != null ? v.getWorker().getFullName() : "")
                .cccdNumberMasked(v.getCccdNumberMasked())
                .fullName(v.getFullName())
                .dob(v.getDob())
                .gender(v.getGender())
                .nationality(v.getNationality())
                .originAddress(v.getOriginAddress())
                .residenceAddress(v.getResidenceAddress())
                .expiryDate(v.getExpiryDate())
                .issueDate(v.getIssueDate())
                .identificationFeatures(v.getIdentificationFeatures())
                .frontImageUrl(signedFront)
                .backImageUrl(signedBack)
                .selfieImageUrl(signedSelfie)
                .faceMatchScore(v.getFaceMatchScore())
                .ocrConfidence(v.getOcrConfidence())
                .fraudFlags(v.getFraudFlags())
                .provider(v.getProvider())
                .failureCode(v.getFailureCode())
                .retryable(EkycVerificationStatus.TECHNICAL_FAILURE.name().equals(v.getStatus()))
                .retryCount(v.getRetryCount())
                .imagesAvailable(hasManagedImages)
                .legalHold(v.isLegalHold())
                .routingDecision(v.getRoutingDecision())
                .routingReason(v.getRoutingReason())
                .status(v.getStatus())
                .reviewedBy(v.getReviewedBy())
                .reviewNotes(v.getReviewNotes())
                .createdAt(v.getCreatedAt())
                .reviewedAt(v.getReviewedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }

    private String resolveSignedImageUrl(WorkerEkycVerification v, String storedKey, String slot, boolean signedLink) {
        if (v.getId() == null || v.getImagesPurgedAt() != null || !imageStorage.isManagedObjectKey(storedKey)) {
            return null;
        }
        if (!signedLink) {
            return "/api/v1/ekyc/images/worker/" + v.getId() + "/" + slot;
        }
        return imageAccessTokenService.generateSignedImageUrl("worker", v.getId(), slot, storedKey, v.getStatus());
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    private void executeInTransaction(Runnable action) {
        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> action.run());
        } else {
            action.run();
        }
    }

    private void recordAudit(UUID actorUserId, String action, UUID entityId, Map<String, Object> metadata) {
        if (auditLogService == null) {
            return;
        }
        auditLogService.logEkycAction(actorUserId, action, "WORKER_EKYC", entityId, metadata);
    }

    private static BigDecimal scaleOrNull(Double val) {
        if (val == null || !Double.isFinite(val)) {
            return null;
        }
        return BigDecimal.valueOf(val).setScale(4, RoundingMode.HALF_UP);
    }

    private record PreparedSubmission(
            UUID workerId,
            UUID verificationId,
            int attempt,
            boolean isRetry,
            String previousFrontKey,
            String previousBackKey,
            String previousSelfieKey
    ) {}
}
