package com.cani.service;

import com.cani.client.ekyc.EkycProvider;
import com.cani.dto.ekyc.EkycAdminReviewRequestDto;
import com.cani.dto.ekyc.EkycConsentPolicyDto;
import com.cani.dto.ekyc.EkycIdentityResultDto;
import com.cani.dto.ekyc.EkycUserSubmittedInput;
import com.cani.dto.ekyc.EoEkycVerificationResultDto;
import com.cani.dto.ekyc.EoProfileDto;
import com.cani.dto.event.EoReviewResponseDto;
import com.cani.entity.EoEkycVerification;
import com.cani.entity.EventOrganizer;
import com.cani.entity.Review;
import com.cani.entity.ShiftAssignment;
import com.cani.entity.User;
import com.cani.entity.enums.EkycRoutingDecision;
import com.cani.entity.enums.EkycVerificationStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.VerificationStatus;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.EkycConsentRecordRepository;
import com.cani.repository.EoEkycVerificationRepository;
import com.cani.repository.EventOrganizerRepository;
import com.cani.repository.SystemConfigRepository;
import com.cani.repository.UserRepository;
import com.cani.service.ekyc.EkycCccdLockManager;
import com.cani.service.ekyc.EkycConsentAndAgeHelper;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventOrganizerEkycService {

    private static final List<String> ACTIVE_DUPLICATE_STATUSES = List.of(
            EkycVerificationStatus.APPROVED.name(),
            EkycVerificationStatus.PENDING_REVIEW.name()
    );

    private static final List<String> RETRYABLE_RECORD_STATUSES = List.of(
            EkycVerificationStatus.TECHNICAL_FAILURE.name(),
            EkycVerificationStatus.PENDING.name(),
            EkycVerificationStatus.PROCESSING.name()
    );

    private final EventOrganizerRepository eventOrganizerRepository;
    private final EoEkycVerificationRepository eoEkycVerificationRepository;
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
    private com.cani.repository.WorkerEkycVerificationRepository workerEkycVerificationRepository;

    @Autowired(required = false)
    private com.cani.repository.WorkerRepository workerRepository;

    @Autowired(required = false)
    private com.cani.repository.EventRepository eventRepository;

    @Autowired(required = false)
    private com.cani.repository.ReviewRepository reviewRepository;

    @Autowired(required = false)
    private com.cani.repository.ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired(required = false)
    private EkycConsentRecordRepository consentRecordRepository;

    @Autowired(required = false)
    private SystemConfigRepository systemConfigRepository;

    @Autowired(required = false)
    private TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    private AuditLogService auditLogService;

    @Value("${cani.ekyc.strict:false}")
    private boolean ekycStrict = false;

    @Value("${cani.ekyc.processing-timeout-seconds:120}")
    private long processingTimeoutSeconds = 120L;

    @Value("${cani.ekyc.consent.required:true}")
    private boolean consentRequired = true;

    @Value("${cani.ekyc.consent.notice-version:2026.11-nd13-v1}")
    private String consentNoticeVersion = EkycConsentAndAgeHelper.DEFAULT_CONSENT_NOTICE_VERSION;

    public EkycConsentPolicyDto getConsentPolicy() {
        return EkycConsentAndAgeHelper.buildConsentPolicy(consentRequired, consentNoticeVersion);
    }

    /**
     * Nhà tổ chức sự kiện nộp hồ sơ định danh eKYC cá nhân
     */
    public EoEkycVerificationResultDto submitEkyc(
            UUID userId,
            MultipartFile frontFile,
            MultipartFile backFile,
            MultipartFile selfieFile) {
        return submitEkyc(userId, frontFile, backFile, selfieFile, null, null, null);
    }

    public EoEkycVerificationResultDto submitEkyc(
            UUID userId,
            MultipartFile frontFile,
            MultipartFile backFile,
            MultipartFile selfieFile,
            String confirmedCccd) {
        return submitEkyc(userId, frontFile, backFile, selfieFile, confirmedCccd, null, null);
    }

    /**
     * Nhà tổ chức sự kiện nộp hồ sơ eKYC qua VNPT eKYC thực tế.
     * Yêu cầu đồng ý xử lý dữ liệu sinh trắc học theo Nghị định 13/2023/NĐ-CP trước khi lưu ảnh / gọi VNPT.
     * Tham số confirmedCccd chỉ dùng để đối chiếu chéo — TUYỆT ĐỐI không ghi đè dữ liệu OCR của VNPT.
     * Phương thức ngoài không giữ DB transaction trong suốt lúc gọi VNPT API.
     */
    public EoEkycVerificationResultDto submitEkyc(
            UUID userId,
            MultipartFile frontFile,
            MultipartFile backFile,
            MultipartFile selfieFile,
            String confirmedCccd,
            Boolean consentAccepted,
            String submittedConsentNoticeVersion) {

        String effectiveConsentVersion = EkycConsentAndAgeHelper.validateConsent(
                consentRequired, consentNoticeVersion, consentAccepted, submittedConsentNoticeVersion);
        Instant consentedAt = Boolean.TRUE.equals(consentAccepted) ? Instant.now() : null;

        // 1. Kiểm tra bảo mật tệp (magic bytes, MIME type, dung lượng tối đa)
        cryptoService.requireIndexKey();
        EkycImageValidator.ValidatedEkycImage validatedFront =
                imageValidator.validate(frontFile, "Ảnh mặt trước CCCD người đại diện");
        EkycImageValidator.ValidatedEkycImage validatedBack =
                imageValidator.validate(backFile, "Ảnh mặt sau CCCD người đại diện");
        EkycImageValidator.ValidatedEkycImage validatedSelfie =
                imageValidator.validate(selfieFile, "Ảnh selfie khuôn mặt người đại diện");

        EkycUserSubmittedInput userInput = EkycUserSubmittedInput.builder()
                .cccdNumber(confirmedCccd)
                .build();

        // 2. Transaction ngắn 1: Chuẩn bị EO & bản ghi PROCESSING với attemptToken chống response trễ / gửi đúp
        PreparedEoSubmission prepared = executeInTransaction(
                () -> prepareSubmissionRecord(userId, effectiveConsentVersion, consentedAt));

        // 3. Lưu ảnh vào Private Object Storage (fail-fast, tuyệt đối không tạo URL giả)
        EkycImageStorage.StoredObject storedFront = null;
        EkycImageStorage.StoredObject storedBack = null;
        EkycImageStorage.StoredObject storedSelfie = null;
        try {
            storedFront = imageStorage.store("eo", prepared.eoId(), "front",
                    validatedFront.getBytes(), validatedFront.getSafeExtension(), validatedFront.getDetectedMimeType());
            storedBack = imageStorage.store("eo", prepared.eoId(), "back",
                    validatedBack.getBytes(), validatedBack.getSafeExtension(), validatedBack.getDetectedMimeType());
            storedSelfie = imageStorage.store("eo", prepared.eoId(), "selfie",
                    validatedSelfie.getBytes(), validatedSelfie.getSafeExtension(), validatedSelfie.getDetectedMimeType());
        } catch (RuntimeException storageEx) {
            imageRetentionService.deleteStoredImagesQuietly(
                    storedFront != null ? storedFront.getObjectKey() : null,
                    storedBack != null ? storedBack.getObjectKey() : null,
                    storedSelfie != null ? storedSelfie.getObjectKey() : null
            );
            executeInTransaction(() -> markTechnicalFailure(
                    prepared.verificationId(), prepared.attempt(), prepared.attemptToken(), "STORAGE_WRITE_FAILED"));
            throw storageEx;
        }

        boolean finalizationStarted = false;
        try {
            // 4. Gọi VNPT eKYC thực tế (ngoài DB transaction)
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

            // 5. Transaction ngắn 2 (khóa đồng bộ theo hash CCCD): Check trùng Worker+EO, chấm quyết định & lưu kết quả
            finalizationStarted = true;
            EoEkycVerificationResultDto result = cccdLockManager.executeWithCccdLock(primaryHash, () ->
                    executeInTransaction(() -> {
                        imageRetentionService.deleteAfterRollback(finalFront.getObjectKey(),
                                finalBack.getObjectKey(), finalSelfie.getObjectKey());
                        return finalizeSubmission(
                            userId,
                            prepared.eoId(),
                            prepared.verificationId(),
                            prepared.attempt(),
                            prepared.attemptToken(),
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
                executeInTransaction(() -> markTechnicalFailure(
                        prepared.verificationId(), prepared.attempt(), prepared.attemptToken(), "FINALIZATION_FAILED"));
            } catch (RuntimeException statusUpdateFailure) {
                failure.addSuppressed(statusUpdateFailure);
            }
            throw failure;
        }
    }

    private PreparedEoSubmission prepareSubmissionRecord(UUID userId, String consentVersion, Instant consentedAt) {
        userRepository.lockForEkyc(userId);
        EventOrganizer eo = getOrCreateEo(userId);
        Instant now = Instant.now();

        EkycConsentAndAgeHelper.persistConsentRecord(
                consentRecordRepository, userId, "EO", consentVersion, consentedAt);

        Optional<EoEkycVerification> latestOpt = eoEkycVerificationRepository
                .findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId());

        if (latestOpt.isPresent()) {
            EoEkycVerification latest = latestOpt.get();
            if (EkycVerificationStatus.PROCESSING.name().equals(latest.getStatus()) && !isProcessingExpired(latest, now)) {
                throw new AppException(
                        "Hồ sơ eKYC trước đó đang được xử lý. Vui lòng đợi hoàn tất hoặc thử lại sau ít phút.",
                        HttpStatus.CONFLICT);
            }
            if (EkycVerificationStatus.PENDING_REVIEW.name().equals(latest.getStatus())
                    && !latest.isLegalHold()
                    && EkycConsentAndAgeHelper.isExpired(latest.getImageRetentionUntil(), latest.getImagesPurgedAt(), now)) {
                latest.setStatus(EkycVerificationStatus.REJECTED.name());
                latest.setRoutingDecision(EkycRoutingDecision.MANUAL_REJECTED.name());
                latest.setRoutingReason("Hồ sơ chờ duyệt trước đó đã hết hạn lưu trữ ảnh. Người dùng nộp lại hồ sơ mới.");
                latest.setImageRetentionUntil(imageRetentionService.calculateRetentionDeadline(EkycVerificationStatus.REJECTED));
                eoEkycVerificationRepository.save(latest);
            }
        }

        Optional<EoEkycVerification> retryCandidate = latestOpt
                .filter(row -> RETRYABLE_RECORD_STATUSES.contains(row.getStatus()));

        String attemptToken = UUID.randomUUID().toString();
        Instant retentionDeadline = imageRetentionService.calculateRetentionDeadline(EkycVerificationStatus.PROCESSING);

        EoEkycVerification record;
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
            record.setProcessingStartedAt(now);
            record.setAttemptToken(attemptToken);
            record.setConsentNoticeVersion(consentVersion);
            record.setConsentedAt(consentedAt);
            record.setImageRetentionUntil(retentionDeadline);
        } else {
            record = EoEkycVerification.builder()
                    .eventOrganizer(eo)
                    .provider(ekycProvider.getProviderName())
                    .status(EkycVerificationStatus.PROCESSING.name())
                    .routingDecision(EkycRoutingDecision.MANUAL_REVIEW.name())
                    .routingReason("Đang gửi hồ sơ xác minh tới VNPT eKYC...")
                    .retryCount(0)
                    .processingStartedAt(now)
                    .attemptToken(attemptToken)
                    .consentNoticeVersion(consentVersion)
                    .consentedAt(consentedAt)
                    .imageRetentionUntil(retentionDeadline)
                    .nationality(null)
                    .build();
        }

        record = eoEkycVerificationRepository.save(record);
        return new PreparedEoSubmission(
                eo.getEoId(), record.getId(), record.getRetryCount(), attemptToken,
                isRetry, prevFront, prevBack, prevSelfie);
    }

    private void markTechnicalFailure(UUID verificationId, Integer attempt, String attemptToken, String code) {
        if (verificationId == null) {
            return;
        }
        eoEkycVerificationRepository.findById(verificationId)
                .filter(record -> !record.isLegalHold()
                        && Objects.equals(record.getRetryCount(), attempt)
                        && (record.getAttemptToken() == null || Objects.equals(record.getAttemptToken(), attemptToken))
                        && EkycVerificationStatus.PROCESSING.name().equals(record.getStatus()))
                .ifPresent(record -> {
            record.setStatus(EkycVerificationStatus.TECHNICAL_FAILURE.name());
            record.setRoutingDecision(EkycRoutingDecision.TECHNICAL_FAILURE.name());
            record.setFailureCode(code);
            record.setImageRetentionUntil(imageRetentionService.calculateRetentionDeadline(EkycVerificationStatus.TECHNICAL_FAILURE));
            record.setRoutingReason("Không thể hoàn tất lưu hồ sơ. Vui lòng thử lại sau.");
            eoEkycVerificationRepository.save(record);
        });
    }

    private EoEkycVerificationResultDto finalizeSubmission(
            UUID userId,
            UUID eoId,
            UUID verificationId,
            int attempt,
            String attemptToken,
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

        EventOrganizer eo = eventOrganizerRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ Nhà tổ chức sự kiện."));

        EoEkycVerification record = eoEkycVerificationRepository.findByIdForUpdate(verificationId)
                .or(() -> eoEkycVerificationRepository.findById(verificationId))
                .orElseThrow(() -> new BadRequestException("Hồ sơ xác minh đã thay đổi."));
        if (record.isLegalHold()
                || !Objects.equals(record.getRetryCount(), attempt)
                || (record.getAttemptToken() != null && !Objects.equals(record.getAttemptToken(), attemptToken))
                || !EkycVerificationStatus.PROCESSING.name().equals(record.getStatus())) {
            throw new BadRequestException("Hồ sơ đã được thay thế, hết hạn xử lý hoặc bảo lưu.");
        }

        boolean isDuplicateCccd = false;
        if (providerResult != null && !providerResult.isTechnicalFailure() && cryptoService.isValidCccdFormat(verifiedRawCccd)) {
            isDuplicateCccd = isCccdDuplicateForEo(eo, userId, verifiedRawCccd, lookupHashes);
        }

        EkycDecisionEngine.DecisionOutcome outcome = decisionEngine.evaluate(
                providerResult,
                userInput,
                isDuplicateCccd,
                ekycStrict
        );

        record.setProvider(providerResult != null && providerResult.getProviderName() != null
                ? providerResult.getProviderName()
                : ekycProvider.getProviderName());
        record.setProviderReference(providerResult != null ? providerResult.getProviderReference() : null);
        record.setFrontImageUrl(frontObjectKey);
        record.setBackImageUrl(backObjectKey);
        record.setSelfieImageUrl(selfieObjectKey);
        record.setImagesPurgedAt(null);
        record.setImageRetentionUntil(imageRetentionService.calculateRetentionDeadline(outcome.getStatus()));
        record.setStatus(outcome.getStatus().name());
        record.setRoutingDecision(outcome.getRoutingDecision().name());
        record.setRoutingReason(outcome.getRoutingReason());
        record.setFailureCode(outcome.getFailureCode());

        // Ngừng ghi các trường OCR thô không cần thiết (giới tính, dân tộc/quốc tịch, quê quán, thường trú, ngày cấp/hết hạn, dob thô)
        record.setDob(null);
        record.setGender(null);
        record.setNationality(null);
        record.setOriginAddress(null);
        record.setResidenceAddress(null);
        record.setExpiryDate(null);
        record.setIssueDate(null);

        Map<String, Object> flags = outcome.getFraudFlags() != null
                ? new LinkedHashMap<>(outcome.getFraudFlags())
                : new LinkedHashMap<>();

        String maskedNumber = cryptoService.maskCccd(verifiedRawCccd);
        if (providerResult != null && !providerResult.isTechnicalFailure()) {
            Boolean meetsMinimumAge = EkycConsentAndAgeHelper.evaluateMeetsMinimumAge(
                    providerResult.getDob(), systemConfigRepository);
            record.setMeetsMinimumAge(meetsMinimumAge);
            if (meetsMinimumAge != null) {
                flags.put("meetsMinimumAge", meetsMinimumAge);
            }
            record.setCccdNumberHash(primaryHash);
            record.setCccdNumberMasked(maskedNumber);
            record.setRepresentativeName(providerResult.getFullName());
            record.setFaceMatchScore(scaleOrNull(providerResult.getFaceMatchScore()));
            record.setOcrConfidence(scaleOrNull(providerResult.getOcrConfidence()));
        } else {
            record.setMeetsMinimumAge(null);
            record.setFaceMatchScore(null);
            record.setOcrConfidence(null);
        }
        record.setFraudFlags(flags);

        record = eoEkycVerificationRepository.save(record);
        imageRetentionService.scheduleImmediatePurgeIfEligible("eo", record.getId(), outcome.getStatus());

        // Đồng bộ trạng thái EO — TUYỆT ĐỐI không hạ cấp tài khoản đã APPROVED
        applyEoAccountStatus(eo, outcome.getStatus(), providerResult);

        log.info("EO eKYC completed [eoId={}, verificationId={}, status={}, decision={}, failureCode={}, retryCount={}]",
                eo.getEoId(), record.getId(), record.getStatus(),
                record.getRoutingDecision(), record.getFailureCode(), record.getRetryCount());

        recordAudit(userId, "EO_EKYC_SUBMITTED", record.getId(),
                Map.of("status", record.getStatus(),
                        "decision", record.getRoutingDecision(),
                        "retryCount", record.getRetryCount() != null ? record.getRetryCount() : 0));

        return mapToDto(record);
    }

    private void applyEoAccountStatus(
            EventOrganizer eo,
            EkycVerificationStatus verificationStatus,
            EkycIdentityResultDto providerResult) {

        boolean alreadyApproved = eo.getVerificationStatus() == VerificationStatus.APPROVED;

        switch (verificationStatus) {
            case APPROVED -> {
                if (providerResult != null) {
                    if (providerResult.getFullName() != null && !providerResult.getFullName().isBlank()
                            && eo.getUser() != null) {
                        eo.getUser().setFullName(providerResult.getFullName().trim());
                        userRepository.save(eo.getUser());
                    }
                }
                eo.setVerificationStatus(VerificationStatus.APPROVED);
                eo.setVerifiedAt(Instant.now());
                eventOrganizerRepository.save(eo);
            }
            case REJECTED -> {
                if (!alreadyApproved) {
                    eo.setVerificationStatus(VerificationStatus.REJECTED);
                    eventOrganizerRepository.save(eo);
                }
            }
            case PENDING, PROCESSING, PENDING_REVIEW -> {
                if (!alreadyApproved) {
                    eo.setVerificationStatus(VerificationStatus.PENDING);
                    eventOrganizerRepository.save(eo);
                }
            }
            case TECHNICAL_FAILURE -> {
                // Không hạ trạng thái đã APPROVED và không chuyển sang REJECTED khi chỉ là lỗi kỹ thuật
                if (eo.getVerificationStatus() == null) {
                    eo.setVerificationStatus(VerificationStatus.PENDING);
                    eventOrganizerRepository.save(eo);
                }
            }
        }
    }

    private boolean isCccdDuplicateForEo(EventOrganizer eo, UUID userId, String rawCccd, Set<String> lookupHashes) {
        if (lookupHashes != null && !lookupHashes.isEmpty()) {
            if (eo.getEoId() != null && eoEkycVerificationRepository
                    .existsByCccdNumberHashInAndStatusInAndEventOrganizer_EoIdNot(
                            lookupHashes, ACTIVE_DUPLICATE_STATUSES, eo.getEoId())) {
                return true;
            }
            if (eoEkycVerificationRepository.existsByCccdNumberHashInAndStatus(
                    lookupHashes, EkycVerificationStatus.APPROVED.name())
                    && eo.getVerificationStatus() != VerificationStatus.APPROVED) {
                return true;
            }
            if (workerEkycVerificationRepository != null && userId != null
                    && workerEkycVerificationRepository.existsByCccdNumberHashInAndStatusInAndWorker_User_UserIdNot(
                            lookupHashes, ACTIVE_DUPLICATE_STATUSES, userId)) {
                return true;
            }
        }
        Set<String> legacyCandidates = cryptoService.buildLegacyCandidateStrings(rawCccd);
        if (workerRepository != null && !legacyCandidates.isEmpty()) {
            return workerRepository.existsByCccdNumberIn(legacyCandidates);
        }
        return false;
    }

    /**
     * Lấy trạng thái hồ sơ eKYC hiện tại của Nhà tổ chức sự kiện (tự động khôi phục nếu PROCESSING quá hạn chờ)
     */
    @Transactional
    public EoEkycVerificationResultDto getMyEkycStatus(UUID userId) {
        EventOrganizer eo = getOrCreateEo(userId);

        Optional<EoEkycVerification> latestOpt = eoEkycVerificationRepository
                .findTopByEventOrganizer_EoIdOrderByCreatedAtDesc(eo.getEoId());
        if (latestOpt.isEmpty()) {
            return EoEkycVerificationResultDto.builder()
                    .eoId(eo.getEoId())
                    .companyName(eo.getCompanyName())
                    .status("NOT_SUBMITTED")
                    .routingReason("Doanh nghiệp chưa nộp hồ sơ xác thực định danh eKYC")
                    .build();
        }
        EoEkycVerification latest = latestOpt.get();
        if (EkycVerificationStatus.PROCESSING.name().equals(latest.getStatus())
                && !latest.isLegalHold()
                && isProcessingExpired(latest, Instant.now())) {
            markStaleProcessingAsTechnicalFailure(latest);
            latest = eoEkycVerificationRepository.save(latest);
        }
        return mapToDto(latest);
    }

    public int recoverStaleProcessingRecords() {
        Instant cutoff = Instant.now().minusSeconds(Math.max(30L, processingTimeoutSeconds));
        List<UUID> staleIds = eoEkycVerificationRepository.findExpiredProcessingIds(cutoff);
        if (staleIds == null || staleIds.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (UUID id : staleIds) {
            Boolean updated = executeInTransaction(() -> recoverSingleStaleProcessing(id, cutoff));
            if (Boolean.TRUE.equals(updated)) {
                recovered++;
            }
        }
        if (recovered > 0) {
            log.warn("Đã tự động khôi phục {} hồ sơ EO eKYC bị kẹt PROCESSING quá {} giây.",
                    recovered, Math.max(30L, processingTimeoutSeconds));
        }
        return recovered;
    }

    private boolean recoverSingleStaleProcessing(UUID id, Instant cutoff) {
        EoEkycVerification record = eoEkycVerificationRepository.findByIdForUpdate(id)
                .or(() -> eoEkycVerificationRepository.findById(id))
                .orElse(null);
        if (record == null || record.isLegalHold()
                || !EkycVerificationStatus.PROCESSING.name().equals(record.getStatus())) {
            return false;
        }
        Instant startedAt = resolveProcessingStartedAt(record);
        if (startedAt != null && !startedAt.isBefore(cutoff)) {
            return false;
        }
        markStaleProcessingAsTechnicalFailure(record);
        eoEkycVerificationRepository.save(record);
        return true;
    }

    private void markStaleProcessingAsTechnicalFailure(EoEkycVerification record) {
        record.setStatus(EkycVerificationStatus.TECHNICAL_FAILURE.name());
        record.setRoutingDecision(EkycRoutingDecision.TECHNICAL_FAILURE.name());
        record.setFailureCode("PROCESSING_TIMEOUT");
        record.setImageRetentionUntil(imageRetentionService.calculateRetentionDeadline(EkycVerificationStatus.TECHNICAL_FAILURE));
        record.setRoutingReason("Phiên xác thực VNPT eKYC bị gián đoạn hoặc quá thời gian xử lý. Vui lòng thử lại.");
    }

    private boolean isProcessingExpired(EoEkycVerification record, Instant now) {
        Instant startedAt = resolveProcessingStartedAt(record);
        if (startedAt == null) {
            return true;
        }
        return !startedAt.plusSeconds(Math.max(30L, processingTimeoutSeconds)).isAfter(now);
    }

    private Instant resolveProcessingStartedAt(EoEkycVerification record) {
        if (record.getProcessingStartedAt() != null) {
            return record.getProcessingStartedAt();
        }
        if (record.getUpdatedAt() != null) {
            return record.getUpdatedAt();
        }
        return record.getCreatedAt();
    }

    /**
     * Lấy thông tin hồ sơ doanh nghiệp (Profile) của EO
     */
    @Transactional
    public EoProfileDto getEoProfile(UUID userId) {
        EventOrganizer eo = getOrCreateEo(userId);
        User u = eo.getUser();

        int actualEventsCount = eo.getTotalEventsCompleted() != null ? eo.getTotalEventsCompleted() : 0;
        int totalEventsCreated = 0;
        int totalConfirmedWorkers = 0;
        if (eventRepository != null) {
            List<com.cani.entity.Event> eoEvents = eventRepository
                    .findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(eo.getEoId());
            totalEventsCreated = (int) eoEvents.stream()
                    .filter(ev -> ev.getStatus() != com.cani.entity.enums.EventStatus.CANCELLED)
                    .count();
            long realCount = eoEvents.stream()
                    .filter(ev -> ev.getStatus() != null
                            && ev.getStatus() != com.cani.entity.enums.EventStatus.DRAFT
                            && ev.getStatus() != com.cani.entity.enums.EventStatus.CANCELLED)
                    .count();
            actualEventsCount = (int) Math.max(actualEventsCount, realCount);
            totalConfirmedWorkers = eoEvents.stream()
                    .filter(ev -> ev.getStatus() != com.cani.entity.enums.EventStatus.CANCELLED)
                    .mapToInt(ev -> ev.getTotalConfirmedWorkers() != null ? ev.getTotalConfirmedWorkers() : 0)
                    .sum();
        }

        BigDecimal actualRatingAvg = eo.getRatingAvg() != null ? eo.getRatingAvg() : BigDecimal.ZERO;
        int actualRatingCount = eo.getRatingCount() != null ? eo.getRatingCount() : 0;
        if (reviewRepository != null) {
            UUID targetUserId = u != null ? u.getUserId() : userId;
            List<com.cani.entity.Review> reviews = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(targetUserId);
            if (reviews != null && !reviews.isEmpty()) {
                actualRatingCount = reviews.size();
                double avg = reviews.stream()
                        .filter(r -> r.getRating() != null)
                        .mapToInt(com.cani.entity.Review::getRating)
                        .average()
                        .orElse(0.0);
                actualRatingAvg = BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP);
            }
        }

        return EoProfileDto.builder()
                .eoId(eo.getEoId())
                .userId(u != null ? u.getUserId() : userId)
                .companyName(eo.getCompanyName())
                .address(eo.getAddress())
                .fullName(u != null ? u.getFullName() : null)
                .phone(u != null ? u.getPhone() : null)
                .email(u != null ? u.getEmail() : null)
                .verificationStatus(eo.getVerificationStatus() != null ? eo.getVerificationStatus().name() : "PENDING")
                .verifiedAt(eo.getVerifiedAt())
                .ratingAvg(actualRatingAvg)
                .ratingCount(actualRatingCount)
                .totalEventsCompleted(actualEventsCount)
                .totalEventsCreated(totalEventsCreated)
                .totalConfirmedWorkers(totalConfirmedWorkers)
                .build();
    }

    /**
     * Lấy danh sách các lượt đánh giá & nhận xét từ Người lao động (Worker) dành cho Nhà tổ chức sự kiện.
     */
    @Transactional(readOnly = true)
    public List<EoReviewResponseDto> getEoReviews(UUID userId) {
        if (userId == null || reviewRepository == null) {
            return List.of();
        }

        List<Review> reviews = new ArrayList<>(reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(userId));
        eventOrganizerRepository.findByUser_UserId(userId).ifPresent(eo -> {
            if (eo.getEoId() != null && !eo.getEoId().equals(userId)) {
                List<Review> eoIdReviews = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(eo.getEoId());
                if (eoIdReviews != null && !eoIdReviews.isEmpty()) {
                    Set<UUID> existingIds = reviews.stream().map(Review::getReviewId).collect(Collectors.toSet());
                    for (Review r : eoIdReviews) {
                        if (existingIds.add(r.getReviewId())) {
                            reviews.add(r);
                        }
                    }
                }
            }
        });

        if (reviews.isEmpty()) {
            return List.of();
        }

        Set<UUID> reviewerIds = reviews.stream()
                .map(Review::getReviewerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, User> userMap = userRepository.findAllById(reviewerIds).stream()
                .collect(Collectors.toMap(User::getUserId, u -> u, (u1, u2) -> u1));

        Map<UUID, com.cani.entity.Worker> workerByUserId = (workerRepository == null || reviewerIds.isEmpty())
                ? Collections.emptyMap()
                : Optional.ofNullable(workerRepository.findByUser_UserIdIn(reviewerIds))
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(w -> w != null && w.getUser() != null && w.getUser().getUserId() != null)
                        .collect(Collectors.toMap(w -> w.getUser().getUserId(), w -> w, (w1, w2) -> w1));

        Set<UUID> assignmentIds = reviews.stream()
                .map(Review::getAssignmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, ShiftAssignment> assignmentMap = Collections.emptyMap();
        if (shiftAssignmentRepository != null && !assignmentIds.isEmpty()) {
            assignmentMap = shiftAssignmentRepository.findAllById(assignmentIds).stream()
                    .collect(Collectors.toMap(ShiftAssignment::getAssignmentId, sa -> sa, (s1, s2) -> s1));
        }

        final Map<UUID, ShiftAssignment> finalAssignmentMap = assignmentMap;
        return reviews.stream()
                .sorted(Comparator.comparing(Review::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(r -> {
                    User reviewer = userMap.get(r.getReviewerId());
                    com.cani.entity.Worker reviewerWorker = r.getReviewerId() != null ? workerByUserId.get(r.getReviewerId()) : null;
                    UUID actualWorkerId = reviewerWorker != null ? reviewerWorker.getWorkerId() : null;
                    String workerName = (reviewer != null && reviewer.getFullName() != null && !reviewer.getFullName().isBlank())
                            ? reviewer.getFullName()
                            : "Nhân sự CANi";
                    String workerAvatar = reviewer != null ? reviewer.getAvatarUrl() : null;

                    String eventName = null;
                    String shiftName = null;
                    if (r.getAssignmentId() != null) {
                        ShiftAssignment sa = finalAssignmentMap.get(r.getAssignmentId());
                        if (sa != null && sa.getShift() != null) {
                            shiftName = sa.getShift().getJobType();
                            if (sa.getShift().getEvent() != null) {
                                eventName = sa.getShift().getEvent().getName();
                            }
                        }
                    }

                    return EoReviewResponseDto.builder()
                            .reviewId(r.getReviewId())
                            .assignmentId(r.getAssignmentId())
                            .eventName(eventName)
                            .shiftName(shiftName)
                            .reviewerUserId(r.getReviewerId())
                            .reviewerWorkerId(actualWorkerId)
                            .reviewerWorkerName(workerName)
                            .workerAvatarUrl(workerAvatar)
                            .rating(r.getRating())
                            .criteriaShiftClarity(r.getCriteriaShiftClarity())
                            .criteriaTiming(r.getCriteriaTiming())
                            .criteriaWorkEnvironment(r.getCriteriaWorkEnvironment())
                            .criteriaSupport(r.getCriteriaSupport())
                            .criteriaPaymentTiming(r.getCriteriaPaymentTiming())
                            .comment(r.getComment())
                            .tags(r.getTags())
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .toList();
    }

    /**
     * Cập nhật thông tin hồ sơ của EO
     */
    @Transactional
    public EoProfileDto updateEoProfile(UUID userId, EoProfileDto dto) {
        EventOrganizer eo = getOrCreateEo(userId);
        if (dto.getCompanyName() != null)
            eo.setCompanyName(dto.getCompanyName().trim());
        if (dto.getAddress() != null)
            eo.setAddress(dto.getAddress().trim());
        eventOrganizerRepository.save(eo);

        User u = eo.getUser();
        if (u != null) {
            boolean userChanged = false;
            if (dto.getFullName() != null && !dto.getFullName().isBlank()) {
                u.setFullName(dto.getFullName().trim());
                userChanged = true;
            }
            if (dto.getPhone() != null && !dto.getPhone().isBlank()) {
                String cleanPhone = dto.getPhone().trim();
                userRepository.findByPhone(cleanPhone).ifPresent(existingUser -> {
                    if (!existingUser.getUserId().equals(u.getUserId())) {
                        throw new BadRequestException("Số điện thoại này đã được sử dụng bởi một tài khoản khác trong hệ thống. Vui lòng kiểm tra lại hoặc sử dụng số khác.");
                    }
                });
                u.setPhone(cleanPhone);
                userChanged = true;
            }
            if (userChanged) {
                userRepository.save(u);
            }
        }

        log.info("EO {} đã cập nhật thông tin doanh nghiệp (Profile)", eo.getEoId());
        return getEoProfile(userId);
    }

    private EventOrganizer getOrCreateEo(UUID userId) {
        return eventOrganizerRepository.findByUser_UserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(
                                    () -> new ResourceNotFoundException("Không tìm thấy thông tin User: " + userId));
                    if (user.getRole() != null && user.getRole() != UserRole.EVENT_ORGANIZER
                            && user.getRole() != UserRole.ADMIN) {
                        throw new BadRequestException("Tài khoản không phải là Nhà tổ chức sự kiện");
                    }
                    return eventOrganizerRepository.save(EventOrganizer.builder()
                            .user(user)
                            .verificationStatus(VerificationStatus.PENDING)
                            .build());
                });
    }

    @Transactional
    public EoEkycVerificationResultDto updateLegalHold(UUID adminUserId, UUID verificationId, boolean legalHold, String reason) {
        EoEkycVerification record = eoEkycVerificationRepository.findByIdForUpdate(verificationId)
                .or(() -> eoEkycVerificationRepository.findById(verificationId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ eKYC có ID: " + verificationId));
        record.setLegalHold(legalHold);
        record = eoEkycVerificationRepository.save(record);
        recordAudit(adminUserId, "EO_EKYC_LEGAL_HOLD_UPDATED", verificationId,
                Map.of("legalHold", legalHold, "reason", reason != null ? reason.trim() : ""));
        log.info("Admin {} updated EO eKYC legalHold={} [verificationId={}]", adminUserId, legalHold, verificationId);
        return mapToDto(record, false);
    }

    /**
     * Lấy danh sách hàng đợi hồ sơ eKYC đang chờ duyệt (PENDING_REVIEW)
     */
    @Transactional(readOnly = true)
    public List<EoEkycVerificationResultDto> getPendingEkycQueue() {
        return eoEkycVerificationRepository.findByStatusOrderByCreatedAtAsc(EkycVerificationStatus.PENDING_REVIEW.name())
                .stream()
                .map(row -> {
                    EoEkycVerificationResultDto dto = mapToDto(row, false);
                    dto.setFrontImageUrl(null);
                    dto.setBackImageUrl(null);
                    dto.setSelfieImageUrl(null);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Admin/Customer Care thẩm định thủ công hồ sơ eKYC của Nhà tổ chức
     */
    @Transactional
    public EoEkycVerificationResultDto adminReviewEkyc(
            UUID adminUserId,
            UUID verificationId,
            EkycAdminReviewRequestDto reviewRequest) {

        EoEkycVerification record = eoEkycVerificationRepository.findByIdForUpdate(verificationId)
                .or(() -> eoEkycVerificationRepository.findById(verificationId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ eKYC có ID: " + verificationId));

        if (!EkycVerificationStatus.PENDING_REVIEW.name().equals(record.getStatus())) {
            throw new BadRequestException("Hồ sơ này đã được xử lý trước đó với trạng thái: " + record.getStatus());
        }

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Admin: " + adminUserId));

        String decision = reviewRequest.getDecision().toUpperCase().trim();
        EventOrganizer eo = record.getEventOrganizer();
        EkycVerificationStatus targetStatus;

        if (EkycVerificationStatus.APPROVED.name().equals(decision)) {
            EkycConsentAndAgeHelper.ImageAvailability availability = EkycConsentAndAgeHelper.evaluateImageAvailability(
                    imageStorage,
                    record.getFrontImageUrl(),
                    record.getBackImageUrl(),
                    record.getSelfieImageUrl(),
                    record.getImageRetentionUntil(),
                    record.getImagesPurgedAt());
            if (!availability.allThreePresentAndReadable()) {
                throw new BadRequestException("Không thể phê duyệt thủ công: " + availability.unavailableReason());
            }
            if (record.getCccdNumberHash() == null || eo == null) {
                throw new BadRequestException("Hồ sơ thiếu số giấy tờ đã đối chiếu. Vui lòng yêu cầu nộp lại hồ sơ.");
            }
            cccdLockManager.acquireDbTransactionLockIfPostgres(record.getCccdNumberHash());
            boolean isDup = eoEkycVerificationRepository.existsByCccdNumberHashAndStatusInAndEventOrganizer_EoIdNot(
                    record.getCccdNumberHash(), List.of(EkycVerificationStatus.APPROVED.name()), eo.getEoId());
            if (!isDup && workerEkycVerificationRepository != null && eo.getUser() != null) {
                isDup = workerEkycVerificationRepository.existsByCccdNumberHashAndStatusInAndWorker_User_UserIdNot(
                        record.getCccdNumberHash(), List.of(EkycVerificationStatus.APPROVED.name()), eo.getUser().getUserId());
            }
            if (isDup) {
                throw new BadRequestException(
                        "Số CCCD này đã được phê duyệt cho một tài khoản khác trong hệ thống! Mỗi CCCD chỉ được định danh cho duy nhất 1 tài khoản.");
            }
            targetStatus = EkycVerificationStatus.APPROVED;
            record.setStatus(EkycVerificationStatus.APPROVED.name());
            record.setRoutingDecision(EkycRoutingDecision.MANUAL_APPROVED.name());
            record.setImageRetentionUntil(imageRetentionService.calculateRetentionDeadline(EkycVerificationStatus.APPROVED));
            if (eo != null) {
                eo.setVerificationStatus(VerificationStatus.APPROVED);
                eo.setVerifiedAt(Instant.now());
                if (eo.getUser() != null && record.getRepresentativeName() != null && !record.getRepresentativeName().isBlank()) {
                    eo.getUser().setFullName(record.getRepresentativeName().trim());
                }
            }
        } else if (EkycVerificationStatus.REJECTED.name().equals(decision)) {
            targetStatus = EkycVerificationStatus.REJECTED;
            record.setStatus(EkycVerificationStatus.REJECTED.name());
            record.setRoutingDecision(EkycRoutingDecision.MANUAL_REJECTED.name());
            record.setImageRetentionUntil(imageRetentionService.calculateRetentionDeadline(EkycVerificationStatus.REJECTED));
            if (eo != null && eo.getVerificationStatus() != VerificationStatus.APPROVED) {
                eo.setVerificationStatus(VerificationStatus.REJECTED);
            }
        } else {
            throw new BadRequestException("Quyết định duyệt chỉ chấp nhận APPROVED hoặc REJECTED");
        }

        record.setReviewedAt(Instant.now());
        record.setReviewedBy(adminUser);
        record.setReviewNotes(reviewRequest.getReviewNotes());

        record = eoEkycVerificationRepository.save(record);
        if (eo != null) {
            eventOrganizerRepository.save(eo);
        }
        imageRetentionService.scheduleImmediatePurgeIfEligible("eo", record.getId(), targetStatus);

        log.info("Admin {} reviewed EO eKYC [verificationId={}, status={}]", adminUserId, verificationId, record.getStatus());
        recordAudit(adminUserId, "EO_EKYC_ADMIN_REVIEWED", verificationId,
                Map.of("status", record.getStatus()));
        return EoEkycVerificationResultDto.builder()
                .verificationId(verificationId)
                .status(record.getStatus())
                .routingDecision(record.getRoutingDecision())
                .reviewedBy(adminUserId)
                .reviewedAt(record.getReviewedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public EoEkycVerificationResultDto getPendingReviewDetails(UUID verificationId) {
        EoEkycVerification record = eoEkycVerificationRepository.findById(verificationId)
                .filter(row -> EkycVerificationStatus.PENDING_REVIEW.name().equals(row.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ eKYC đang chờ duyệt."));
        return mapToDto(record, false);
    }

    private EoEkycVerificationResultDto mapToDto(EoEkycVerification record) {
        return mapToDto(record, true);
    }

    private EoEkycVerificationResultDto mapToDto(EoEkycVerification record, boolean signedLinks) {
        EkycConsentAndAgeHelper.ImageAvailability availability = EkycConsentAndAgeHelper.evaluateImageAvailability(
                imageStorage,
                record.getFrontImageUrl(),
                record.getBackImageUrl(),
                record.getSelfieImageUrl(),
                record.getImageRetentionUntil(),
                record.getImagesPurgedAt());

        String signedFront = availability.expired() ? null : resolveSignedImageUrl(record, record.getFrontImageUrl(), "front", signedLinks);
        String signedBack = availability.expired() ? null : resolveSignedImageUrl(record, record.getBackImageUrl(), "back", signedLinks);
        String signedSelfie = availability.expired() ? null : resolveSignedImageUrl(record, record.getSelfieImageUrl(), "selfie", signedLinks);

        Boolean meetsMinimumAge = record.getMeetsMinimumAge() != null
                ? record.getMeetsMinimumAge()
                : EkycConsentAndAgeHelper.evaluateMeetsMinimumAge(record.getDob(), systemConfigRepository);

        return EoEkycVerificationResultDto.builder()
                .verificationId(record.getId())
                .eoId(record.getEventOrganizer() != null ? record.getEventOrganizer().getEoId() : null)
                .companyName(record.getEventOrganizer() != null ? record.getEventOrganizer().getCompanyName() : null)
                .representativeName(record.getRepresentativeName())
                .cccdNumberMasked(record.getCccdNumberMasked())
                .dob(record.getDob())
                .gender(record.getGender())
                .nationality(record.getNationality())
                .originAddress(record.getOriginAddress())
                .residenceAddress(record.getResidenceAddress())
                .expiryDate(record.getExpiryDate())
                .issueDate(record.getIssueDate())
                .frontImageUrl(signedFront)
                .backImageUrl(signedBack)
                .selfieImageUrl(signedSelfie)
                .faceMatchScore(record.getFaceMatchScore())
                .ocrConfidence(record.getOcrConfidence())
                .fraudFlags(record.getFraudFlags())
                .provider(record.getProvider())
                .failureCode(record.getFailureCode())
                .retryable(EkycVerificationStatus.TECHNICAL_FAILURE.name().equals(record.getStatus()))
                .retryCount(record.getRetryCount())
                .imagesAvailable(availability.available())
                .imagesExpired(availability.expired())
                .imagesUnavailableReason(availability.unavailableReason())
                .imageRetentionUntil(record.getImageRetentionUntil())
                .legalHold(record.isLegalHold())
                .meetsMinimumAge(meetsMinimumAge)
                .consentNoticeVersion(record.getConsentNoticeVersion())
                .consentedAt(record.getConsentedAt())
                .routingDecision(record.getRoutingDecision())
                .routingReason(record.getRoutingReason())
                .status(record.getStatus())
                .reviewedBy(record.getReviewedBy() != null ? record.getReviewedBy().getUserId() : null)
                .reviewNotes(record.getReviewNotes())
                .createdAt(record.getCreatedAt())
                .reviewedAt(record.getReviewedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private String resolveSignedImageUrl(EoEkycVerification v, String storedKey, String slot, boolean signedLink) {
        if (v.getId() == null || v.getImagesPurgedAt() != null || !imageStorage.isManagedObjectKey(storedKey)) {
            return null;
        }
        if (!signedLink) {
            return "/api/v1/ekyc/images/eo/" + v.getId() + "/" + slot;
        }
        return imageAccessTokenService.generateSignedImageUrl("eo", v.getId(), slot, storedKey, v.getStatus());
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
        auditLogService.logEkycAction(actorUserId, action, "EO_EKYC", entityId, metadata);
    }

    private static BigDecimal scaleOrNull(Double val) {
        if (val == null || !Double.isFinite(val)) {
            return null;
        }
        return BigDecimal.valueOf(val).setScale(4, RoundingMode.HALF_UP);
    }

    private record PreparedEoSubmission(
            UUID eoId,
            UUID verificationId,
            int attempt,
            String attemptToken,
            boolean isRetry,
            String previousFrontKey,
            String previousBackKey,
            String previousSelfieKey
    ) {}
}
