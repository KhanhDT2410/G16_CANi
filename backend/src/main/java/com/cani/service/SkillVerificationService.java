package com.cani.service;

import com.cani.client.skill.CertificateLookupSource;
import com.cani.client.skill.CertificateLookupSource.CertificateLookupOutcome;
import com.cani.client.skill.CertificateLookupSource.CertificateLookupRequest;
import com.cani.client.skill.CertificateLookupSource.CertificateLookupResult;
import com.cani.client.skill.UnsupportedCertificateLookupSource;
import com.cani.dto.skill.*;
import com.cani.entity.AuditLog;
import com.cani.entity.Skill;
import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.SystemConfig;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerEkycVerification;
import com.cani.entity.WorkerSkill;
import com.cani.entity.WorkerSkillPoint;
import com.cani.entity.enums.DocumentAuthenticityStatus;
import com.cani.entity.enums.IdentityVerificationStatus;
import com.cani.entity.enums.IssuerVerificationStatus;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.VerificationTier;
import com.cani.exception.AppException;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.SkillRepository;
import com.cani.repository.SkillVerificationRequestRepository;
import com.cani.repository.SystemConfigRepository;
import com.cani.repository.WorkerEkycVerificationRepository;
import com.cani.repository.WorkerRepository;
import com.cani.repository.WorkerSkillPointRepository;
import com.cani.repository.WorkerSkillRepository;
import com.cani.service.skill.CertificateStorageService;
import com.cani.service.skill.DocumentAccessTokenService;
import com.cani.service.skill.FileSecurityValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.cani.service.skill.eval.DocumentExtractionResult;
import com.cani.service.skill.eval.SkillDocumentExtractor;

/**
 * Orchestrator cho module Xác minh tự động Chứng chỉ Kỹ năng Worker (UC-43 & UC-46).
 * - FileSecurityValidator + CertificateStorageService + DocumentAccessTokenService (Bảo mật tệp)
 * - SkillDocumentExtractor (OCR / Vision đa phương thức không dùng Gemini, tách biệt hoàn toàn với VNPT eKYC):
 *   Chỉ trích xuất dữ liệu có cấu trúc; confidence hay kỹ năng tự khai trên CV không tự cấp điểm SP.
 * - CertificateLookupSource (Nguồn tra cứu chứng chỉ chính thức):
 *   Chỉ cấp trạng thái APPROVED & SP_verify khi nguồn chính thức trả VERIFIED, Worker có danh tính eKYC đã xác thực
 *   khớp tên người nhận, hạn hiệu lực hợp lệ (strict date / nguồn xác nhận vô thời hạn) & khớp kỹ năng yêu cầu.
 * - Khi chưa tích hợp nguồn cấp chính thức hoặc thiếu AI provider/credentials:
 *   trả "Chưa thể xác minh tự động" (0 SP) một cách trung thực (không đưa vào hàng đợi duyệt tay).
 * - Khi nguồn tra cứu lỗi tạm thời: trả VERIFICATION_FAILED để Worker thử lại (retry).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillVerificationService {

    private static final String DEFAULT_UNSPECIFIED_JOB_TYPE = "Chưa xác định";
    private static final String AUDIT_ENTITY_TYPE = "SKILL_VERIFICATION_REQUEST";
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final List<SkillVerificationStatus> ACTIVE_DUPLICATE_STATUSES = List.of(
            SkillVerificationStatus.APPROVED
    );
    private static final List<DateTimeFormatter> SUPPORTED_DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)
    );

    private final WorkerRepository workerRepository;
    private final SkillRepository skillRepository;
    private final WorkerSkillRepository workerSkillRepository;
    private final WorkerSkillPointRepository workerSkillPointRepository;
    private final SkillVerificationRequestRepository verificationRequestRepository;
    private final ObjectMapper objectMapper;
    private final FileSecurityValidator fileSecurityValidator;
    private final CertificateStorageService certificateStorageService;
    private final DocumentAccessTokenService documentAccessTokenService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;
    private final CertificateLookupSource certificateLookupSource;

    @Autowired(required = false)
    private SystemConfigRepository systemConfigRepository;

    @Autowired(required = false)
    private WorkerEkycVerificationRepository workerEkycVerificationRepository;

    @Autowired(required = false)
    private SkillDocumentExtractor skillDocumentExtractor;

    public void setSystemConfigRepository(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    public void setWorkerEkycVerificationRepository(WorkerEkycVerificationRepository workerEkycVerificationRepository) {
        this.workerEkycVerificationRepository = workerEkycVerificationRepository;
    }

    public void setSkillDocumentExtractor(SkillDocumentExtractor skillDocumentExtractor) {
        this.skillDocumentExtractor = skillDocumentExtractor;
    }

    private int getAiDocumentSp() {
        if (systemConfigRepository == null) {
            return VerificationTier.AI_DOCUMENT.getSpVerify();
        }
        try {
            return systemConfigRepository.findByKey("skill_verify_sp_ai_document")
                    .map(SystemConfig::getValue)
                    .map(String::trim)
                    .filter(val -> !val.isEmpty())
                    .map(Integer::parseInt)
                    .filter(val -> val >= 0)
                    .orElse(VerificationTier.AI_DOCUMENT.getSpVerify());
        } catch (Exception e) {
            log.warn("Lỗi khi đọc cấu hình skill_verify_sp_ai_document, fallback về {}: {}",
                    VerificationTier.AI_DOCUMENT.getSpVerify(), e.getMessage());
            return VerificationTier.AI_DOCUMENT.getSpVerify();
        }
    }

    private int getPlatformProvenSp() {
        if (systemConfigRepository == null) {
            return VerificationTier.PLATFORM_PROVEN.getSpVerify();
        }
        try {
            return systemConfigRepository.findByKey("skill_verify_sp_platform_proven")
                    .map(SystemConfig::getValue)
                    .map(String::trim)
                    .filter(val -> !val.isEmpty())
                    .map(Integer::parseInt)
                    .filter(val -> val >= 0)
                    .orElse(VerificationTier.PLATFORM_PROVEN.getSpVerify());
        } catch (Exception e) {
            log.warn("Lỗi khi đọc cấu hình skill_verify_sp_platform_proven, fallback về {}: {}",
                    VerificationTier.PLATFORM_PROVEN.getSpVerify(), e.getMessage());
            return VerificationTier.PLATFORM_PROVEN.getSpVerify();
        }
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    private void deleteStoredDocumentAfterRollback(String storedDocumentUrl) {
        String filename = documentAccessTokenService != null
                ? documentAccessTokenService.extractFilename(storedDocumentUrl)
                : null;
        if (filename == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    certificateStorageService.deleteQuietly(filename);
                }
            }
        });
    }

    private void deleteStoredDocumentAfterCommit(String storedDocumentUrl) {
        String filename = documentAccessTokenService != null
                ? documentAccessTokenService.extractFilename(storedDocumentUrl)
                : null;
        if (filename == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            certificateStorageService.deleteQuietly(filename);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                certificateStorageService.deleteQuietly(filename);
            }
        });
    }

    /**
     * Nộp chứng chỉ nghề nghiệp để trích xuất tài liệu (OCR/Vision nếu cấu hình)
     * và xác minh tự động qua nguồn tra cứu chính thức (UC-43 / UC-46).
     */
    public SkillVerificationResponseDto submitVerification(
            UUID workerId,
            MultipartFile file,
            UUID requestedSkillId,
            String notes
    ) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Worker với ID: " + workerId));

        // 1. Kiểm tra bảo mật tệp (Magic Bytes, Size <= 5MB, chống extension spoofing, tính SHA-256)
        FileSecurityValidator.ValidatedDocument validatedDoc = fileSecurityValidator.validate(file);

        // 2. Chống nộp trùng tài liệu đã được phê duyệt trước đó
        if (verificationRequestRepository.existsByWorker_WorkerIdAndDocumentHashAndStatusIn(
                workerId, validatedDoc.getSha256Hash(), ACTIVE_DUPLICATE_STATUSES)) {
            throw new AppException(
                    "Tài liệu chứng chỉ này đã được xác minh và phê duyệt trước đó.",
                    HttpStatus.CONFLICT
            );
        }

        Skill requestedSkill = requestedSkillId != null
                ? skillRepository.findById(requestedSkillId).orElse(null)
                : null;
        String verifiedEkycName = resolveVerifiedEkycFullName(worker).orElse("");

        // 3. Lưu tệp riêng tư bằng UUID filename (fail-fast nếu lỗi ghi đĩa)
        String storedDocumentUrl = certificateStorageService.storeDocument(
                validatedDoc.getBytes(),
                validatedDoc.getSafeExtension()
        );

        // 4. Trích xuất cấu trúc qua AI (ngoài transaction DB; KHÔNG decode PDF/ảnh thành UTF-8)
        DocumentExtractionResult extractionResult = extractDocumentSafely(
                validatedDoc.getBytes(),
                validatedDoc.getSanitizedOriginalFilename(),
                validatedDoc.getDetectedMimeType()
        );

        // 5. Tra cứu chứng chỉ qua nguồn chính thức ngoài transaction DB
        CertificateLookupResult lookupResult = executeLookupSafely(buildLookupRequest(
                null,
                workerId,
                verifiedEkycName,
                validatedDoc.getSanitizedOriginalFilename(),
                validatedDoc.getDetectedMimeType(),
                validatedDoc.getSha256Hash(),
                validatedDoc.getBytes(),
                requestedSkill != null ? requestedSkill.getSkillId() : requestedSkillId,
                requestedSkill != null ? requestedSkill.getName() : null,
                notes,
                extractionResult
        ));

        try {
            return executeInTransaction(() -> {
                deleteStoredDocumentAfterRollback(storedDocumentUrl);
                return persistAutomatedVerification(
                        workerId,
                        requestedSkill,
                        validatedDoc,
                        storedDocumentUrl,
                        notes,
                        extractionResult,
                        lookupResult
                );
            });
        } catch (RuntimeException ex) {
            String filename = documentAccessTokenService != null
                    ? documentAccessTokenService.extractFilename(storedDocumentUrl)
                    : null;
            certificateStorageService.deleteQuietly(filename);
            throw ex;
        }
    }

    private record ResubmitContext(
            String workerFullName,
            UUID requestedSkillId,
            String jobType,
            String documentUrl,
            String documentName,
            String documentType,
            String documentHash,
            String workerNotes
    ) {}

    /**
     * Worker nộp lại chứng chỉ mới khi hồ sơ ở trạng thái chưa xác minh / lỗi / cần bổ sung.
     */
    public SkillVerificationResponseDto resubmitVerification(
            UUID verificationId,
            UUID workerId,
            MultipartFile file,
            String notes
    ) {
        return resubmitVerification(verificationId, workerId, file, null, notes);
    }

    /**
     * Worker nộp lại chứng chỉ mới đè vào hồ sơ cũ, cho phép chọn lại kỹ năng mục tiêu nếu cần.
     */
    public SkillVerificationResponseDto resubmitVerification(
            UUID verificationId,
            UUID workerId,
            MultipartFile file,
            UUID requestedSkillId,
            String notes
    ) {
        ResubmitContext ctx = executeInTransaction(() -> {
            SkillVerificationRequest existing = verificationRequestRepository.findById(verificationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thẩm định với ID: " + verificationId));

            if (!existing.getWorker().getWorkerId().equals(workerId)) {
                throw new AppException("Bạn không có quyền bổ sung tài liệu cho hồ sơ của Worker khác.", HttpStatus.FORBIDDEN);
            }
            if (existing.getStatus() == SkillVerificationStatus.APPROVED
                    || existing.getStatus() == SkillVerificationStatus.REVOKED) {
                throw new BadRequestException("Hồ sơ đã ở trạng thái cuối (" + existing.getStatus() + "), không thể nộp lại.");
            }
            String wName = existing.getWorker() != null ? resolveVerifiedEkycFullName(existing.getWorker()).orElse("") : "";
            UUID reqSkillId = existing.getSkill() != null ? existing.getSkill().getSkillId() : null;
            return new ResubmitContext(
                    wName,
                    reqSkillId,
                    reqSkillId != null ? existing.getSkill().getName() : existing.getJobType(),
                    existing.getDocumentUrl(),
                    existing.getDocumentName(),
                    existing.getDocumentType(),
                    existing.getDocumentHash(),
                    existing.getWorkerNotes()
            );
        });

        FileSecurityValidator.ValidatedDocument validatedDoc = fileSecurityValidator.validate(file);

        if (verificationRequestRepository.existsByWorker_WorkerIdAndDocumentHashAndStatusIn(
                workerId, validatedDoc.getSha256Hash(), ACTIVE_DUPLICATE_STATUSES)) {
            throw new AppException("Tài liệu này đã tồn tại trong một hồ sơ đã được phê duyệt.", HttpStatus.CONFLICT);
        }

        Skill effectiveSkill = resolveEffectiveResubmitSkill(requestedSkillId, ctx.requestedSkillId());
        UUID effectiveSkillId = effectiveSkill != null ? effectiveSkill.getSkillId() : ctx.requestedSkillId();
        String effectiveSkillName = effectiveSkill != null ? effectiveSkill.getName() : ctx.jobType();

        String storedDocumentUrl = certificateStorageService.storeDocument(
                validatedDoc.getBytes(),
                validatedDoc.getSafeExtension()
        );

        DocumentExtractionResult extractionResult = extractDocumentSafely(
                validatedDoc.getBytes(),
                validatedDoc.getSanitizedOriginalFilename(),
                validatedDoc.getDetectedMimeType()
        );

        CertificateLookupResult lookupResult = executeLookupSafely(buildLookupRequest(
                verificationId,
                workerId,
                ctx.workerFullName(),
                validatedDoc.getSanitizedOriginalFilename(),
                validatedDoc.getDetectedMimeType(),
                validatedDoc.getSha256Hash(),
                validatedDoc.getBytes(),
                effectiveSkillId,
                effectiveSkillName,
                notes,
                extractionResult
        ));

        try {
            return executeInTransaction(() -> {
                deleteStoredDocumentAfterRollback(storedDocumentUrl);
                return updateResubmittedVerification(
                        verificationId, workerId, effectiveSkill, validatedDoc, storedDocumentUrl, notes, extractionResult, lookupResult
                );
            });
        } catch (RuntimeException ex) {
            String filename = documentAccessTokenService != null
                    ? documentAccessTokenService.extractFilename(storedDocumentUrl)
                    : null;
            certificateStorageService.deleteQuietly(filename);
            throw ex;
        }
    }

    /**
     * Worker thử lại xác minh tự động đối với hồ sơ đang gặp lỗi tạm thời (VERIFICATION_FAILED).
     */
    public SkillVerificationResponseDto retryVerification(UUID verificationId, UUID workerId) {
        ResubmitContext ctx = executeInTransaction(() -> {
            SkillVerificationRequest req = verificationRequestRepository.findById(verificationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thẩm định với ID: " + verificationId));
            if (!req.getWorker().getWorkerId().equals(workerId)) {
                throw new AppException("Bạn không có quyền thử lại hồ sơ của Worker khác.", HttpStatus.FORBIDDEN);
            }
            if (req.getStatus() != SkillVerificationStatus.VERIFICATION_FAILED) {
                throw new BadRequestException("Chỉ có thể thử lại khi hồ sơ đang gặp lỗi tạm thời (VERIFICATION_FAILED).");
            }
            String wName = req.getWorker() != null ? resolveVerifiedEkycFullName(req.getWorker()).orElse("") : "";
            UUID reqSkillId = req.getSkill() != null ? req.getSkill().getSkillId() : null;
            return new ResubmitContext(
                    wName,
                    reqSkillId,
                    reqSkillId != null ? req.getSkill().getName() : req.getJobType(),
                    req.getDocumentUrl(),
                    req.getDocumentName(),
                    req.getDocumentType(),
                    req.getDocumentHash(),
                    req.getWorkerNotes()
            );
        });

        String filename = documentAccessTokenService != null
                ? documentAccessTokenService.extractFilename(ctx.documentUrl())
                : null;
        if (filename == null || filename.isBlank()) {
            throw new BadRequestException("Hồ sơ không còn tệp chứng chỉ để thử lại. Vui lòng nộp lại chứng chỉ mới.");
        }
        byte[] storedBytes = certificateStorageService.readBytes(filename);

        DocumentExtractionResult extractionResult = extractDocumentSafely(
                storedBytes,
                ctx.documentName(),
                ctx.documentType()
        );

        CertificateLookupResult lookupResult = executeLookupSafely(buildLookupRequest(
                verificationId,
                workerId,
                ctx.workerFullName(),
                ctx.documentName(),
                ctx.documentType(),
                ctx.documentHash(),
                storedBytes,
                ctx.requestedSkillId(),
                ctx.jobType(),
                ctx.workerNotes(),
                extractionResult
        ));

        return executeInTransaction(() -> {
            SkillVerificationRequest req = verificationRequestRepository.findById(verificationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thẩm định với ID: " + verificationId));
            if (!req.getWorker().getWorkerId().equals(workerId)) {
                throw new AppException("Bạn không có quyền thử lại hồ sơ của Worker khác.", HttpStatus.FORBIDDEN);
            }
            if (req.getStatus() != SkillVerificationStatus.VERIFICATION_FAILED) {
                throw new BadRequestException("Trạng thái hồ sơ đã thay đổi, không thể thử lại.");
            }
            Worker worker = req.getWorker();
            req.setRetryCount((req.getRetryCount() != null ? req.getRetryCount() : 0) + 1);
            applyExtractionMetadata(req, extractionResult);
            applyLookupEvaluation(req, worker, req.getSkill(), lookupResult, extractionResult);
            SkillVerificationRequest saved = verificationRequestRepository.save(req);

            recordAudit(
                    worker.getUser() != null ? worker.getUser().getUserId() : null,
                    "SKILL_VERIFICATION_RETRIED",
                    saved.getId(),
                    Map.of("status", SkillVerificationStatus.VERIFICATION_FAILED.name()),
                    Map.of("status", saved.getStatus().name(), "retryCount", saved.getRetryCount(),
                            "spAwarded", saved.getSpAwarded() != null ? saved.getSpAwarded() : 0)
            );
            return mapToResponseDto(saved, null, null, worker);
        });
    }

    private Skill resolveEffectiveResubmitSkill(UUID requestedSkillId, UUID fallbackSkillId) {
        UUID targetId = requestedSkillId != null ? requestedSkillId : fallbackSkillId;
        if (targetId == null) {
            return null;
        }
        return skillRepository.findById(targetId).orElse(null);
    }

    private CertificateLookupRequest buildLookupRequest(
            UUID verificationId,
            UUID workerId,
            String workerFullName,
            String documentName,
            String mimeType,
            String sha256Hash,
            byte[] fileBytes,
            UUID requestedSkillId,
            String requestedSkillName,
            String notes,
            DocumentExtractionResult extractionResult
    ) {
        return CertificateLookupRequest.builder()
                .verificationId(verificationId)
                .workerId(workerId)
                .workerFullName(workerFullName)
                .documentName(documentName)
                .mimeType(mimeType)
                .sha256Hash(sha256Hash)
                .fileBytes(fileBytes)
                .requestedSkillId(requestedSkillId)
                .requestedSkillName(requestedSkillName)
                .notes(notes)
                .extractedDocumentType(extractionResult != null ? extractionResult.getDocumentType() : null)
                .extractedRecipientName(extractionResult != null ? extractionResult.getRecipientName() : null)
                .extractedInstitution(extractionResult != null ? extractionResult.getInstitution() : null)
                .extractedCertificateNumber(extractionResult != null ? extractionResult.getCertificateNumber() : null)
                .extractedExpiryDate(extractionResult != null ? extractionResult.getExpiryDate() : null)
                .extractedSkills(extractionResult != null ? extractionResult.getExtractedSkills() : null)
                .build();
    }

    private CertificateLookupResult executeLookupSafely(CertificateLookupRequest request) {
        if (certificateLookupSource == null) {
            return new UnsupportedCertificateLookupSource().verifyCertificate(request);
        }
        try {
            CertificateLookupResult result = certificateLookupSource.verifyCertificate(request);
            if (result == null || result.getOutcome() == null) {
                return new UnsupportedCertificateLookupSource().verifyCertificate(request);
            }
            return result;
        } catch (RuntimeException ex) {
            log.warn("Lỗi tạm thời khi tra cứu nguồn xác thực chứng chỉ [workerId={}]: {}",
                    request.getWorkerId(), ex.getMessage());
            return CertificateLookupResult.builder()
                    .outcome(CertificateLookupOutcome.TEMPORARY_FAILURE)
                    .verificationSource("LOOKUP_ERROR")
                    .failureCode("LOOKUP_TEMPORARY_ERROR")
                    .reason("Nguồn tra cứu chứng chỉ đang gặp lỗi kết nối tạm thời. Vui lòng thử lại sau ít phút.")
                    .build();
        }
    }

    @Transactional
    protected SkillVerificationResponseDto persistAutomatedVerification(
            UUID workerId,
            Skill requestedSkill,
            FileSecurityValidator.ValidatedDocument validatedDoc,
            String storedDocumentUrl,
            String notes,
            DocumentExtractionResult extractionResult,
            CertificateLookupResult lookupResult
    ) {
        if (verificationRequestRepository.existsByWorker_WorkerIdAndDocumentHashAndStatusIn(
                workerId, validatedDoc.getSha256Hash(), ACTIVE_DUPLICATE_STATUSES)) {
            throw new AppException(
                    "Tài liệu chứng chỉ này đã được xác minh và phê duyệt trước đó.",
                    HttpStatus.CONFLICT
            );
        }

        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + workerId));

        String resolvedJobType = (requestedSkill != null && requestedSkill.getName() != null && !requestedSkill.getName().isBlank())
                ? requestedSkill.getName()
                : DEFAULT_UNSPECIFIED_JOB_TYPE;

        SkillVerificationRequest entity = SkillVerificationRequest.builder()
                .worker(worker)
                .skill(requestedSkill)
                .jobType(resolvedJobType)
                .documentUrl(storedDocumentUrl)
                .documentName(validatedDoc.getSanitizedOriginalFilename())
                .documentType(validatedDoc.getDetectedMimeType())
                .documentHash(validatedDoc.getSha256Hash())
                .hasSealOrSignature(false)
                .routingDecision(RoutingDecision.REJECT)
                .status(SkillVerificationStatus.REJECTED)
                .humanReviewed(false)
                .spAwarded(0)
                .workerNotes(notes)
                .retryCount(0)
                .build();

        applyExtractionMetadata(entity, extractionResult);
        SkillVerificationRequest saved = verificationRequestRepository.save(entity);
        applyLookupEvaluation(saved, worker, requestedSkill, lookupResult, extractionResult);
        saved = verificationRequestRepository.save(saved);

        recordAudit(
                worker.getUser() != null ? worker.getUser().getUserId() : null,
                "SKILL_VERIFICATION_SUBMITTED",
                saved.getId(),
                null,
                Map.of(
                        "status", saved.getStatus().name(),
                        "routingDecision", saved.getRoutingDecision().name(),
                        "verificationSource", saved.getVerificationSource() != null ? saved.getVerificationSource() : "",
                        "spAwarded", saved.getSpAwarded() != null ? saved.getSpAwarded() : 0
                )
        );

        log.info("Automated skill verification completed [requestId={}, workerId={}, status={}, source={}, sp={}]",
                saved.getId(), workerId, saved.getStatus(), saved.getVerificationSource(), saved.getSpAwarded());

        return mapToResponseDto(saved, null, null, worker);
    }

    @Transactional
    protected SkillVerificationResponseDto updateResubmittedVerification(
            UUID verificationId,
            UUID workerId,
            Skill effectiveSkill,
            FileSecurityValidator.ValidatedDocument validatedDoc,
            String storedDocumentUrl,
            String notes,
            DocumentExtractionResult extractionResult,
            CertificateLookupResult lookupResult
    ) {
        SkillVerificationRequest req = verificationRequestRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu: " + verificationId));
        if (!req.getWorker().getWorkerId().equals(workerId)) {
            throw new AppException("Bạn không có quyền bổ sung tài liệu cho hồ sơ của Worker khác.", HttpStatus.FORBIDDEN);
        }
        if (req.getStatus() == SkillVerificationStatus.APPROVED
                || req.getStatus() == SkillVerificationStatus.REVOKED) {
            throw new BadRequestException("Trạng thái hồ sơ đã thay đổi, không thể nộp lại.");
        }
        if (verificationRequestRepository.existsByWorker_WorkerIdAndDocumentHashAndStatusIn(
                workerId, validatedDoc.getSha256Hash(), ACTIVE_DUPLICATE_STATUSES)) {
            throw new AppException("Tài liệu này đã tồn tại trong một hồ sơ đã phê duyệt.", HttpStatus.CONFLICT);
        }

        String oldStatus = req.getStatus().name();
        String previousDocumentUrl = req.getDocumentUrl();
        Worker worker = req.getWorker();

        if (effectiveSkill != null) {
            req.setSkill(effectiveSkill);
            req.setJobType(effectiveSkill.getName() != null && !effectiveSkill.getName().isBlank()
                    ? effectiveSkill.getName()
                    : DEFAULT_UNSPECIFIED_JOB_TYPE);
        }
        req.setDocumentUrl(storedDocumentUrl);
        req.setDocumentName(validatedDoc.getSanitizedOriginalFilename());
        req.setDocumentType(validatedDoc.getDetectedMimeType());
        req.setDocumentHash(validatedDoc.getSha256Hash());
        if (notes != null) {
            req.setWorkerNotes(notes);
        }
        req.setExtractedSkills(null);
        req.setRecipientName(null);
        req.setInstitution(null);
        req.setCertificateNumber(null);
        req.setIssueDate(null);
        req.setExpiryDate(null);
        req.setFailureReason(null);
        req.setRetryCount(0);
        req.setHumanReviewed(false);
        req.setReviewedBy(null);
        req.setReviewedAt(null);
        req.setReviewNotes(null);
        req.setSpAwarded(0);

        applyExtractionMetadata(req, extractionResult);
        applyLookupEvaluation(req, worker, req.getSkill(), lookupResult, extractionResult);
        SkillVerificationRequest saved = verificationRequestRepository.save(req);

        if (previousDocumentUrl != null && !previousDocumentUrl.equals(storedDocumentUrl)) {
            deleteStoredDocumentAfterCommit(previousDocumentUrl);
        }

        recordAudit(
                worker.getUser() != null ? worker.getUser().getUserId() : null,
                "SKILL_VERIFICATION_RESUBMITTED",
                saved.getId(),
                Map.of("status", oldStatus),
                Map.of(
                        "status", saved.getStatus().name(),
                        "verificationSource", saved.getVerificationSource() != null ? saved.getVerificationSource() : "",
                        "spAwarded", saved.getSpAwarded() != null ? saved.getSpAwarded() : 0
                )
        );

        return mapToResponseDto(saved, null, null, worker);
    }

    private DocumentExtractionResult extractDocumentSafely(byte[] bytes, String filename, String mimeType) {
        if (skillDocumentExtractor == null) {
            return null;
        }
        try {
            return skillDocumentExtractor.extract(bytes, filename, mimeType, SkillDocumentExtractor.PROMPT_V2);
        } catch (RuntimeException ex) {
            log.warn("Lỗi trích xuất tài liệu bằng AI [file={}]: {}", filename, ex.getMessage());
            return DocumentExtractionResult.builder()
                    .status(DocumentExtractionResult.ExtractionStatus.UNCONFIGURED)
                    .documentType("UNKNOWN")
                    .modelName("NONE")
                    .failureMessage(ex.getMessage())
                    .build();
        }
    }

    private void applyExtractionMetadata(SkillVerificationRequest entity, DocumentExtractionResult extraction) {
        if (extraction == null) {
            return;
        }
        if (extraction.getModelName() != null
                && !extraction.getModelName().isBlank()
                && !"NONE".equalsIgnoreCase(extraction.getModelName().trim())) {
            entity.setModelName(extraction.getModelName());
        }
        if (extraction.getPromptVersion() != null && !extraction.getPromptVersion().isBlank()) {
            entity.setPromptVersion(extraction.getPromptVersion());
        }
        if (extraction.getStatus() == DocumentExtractionResult.ExtractionStatus.SUCCESS
                && extraction.getExtractedSkills() != null
                && !extraction.getExtractedSkills().isEmpty()) {
            try {
                String snippet = extraction.getEvidenceSnippets() != null
                        ? extraction.getEvidenceSnippets().get("skills")
                        : null;
                List<ExtractedSkillDto> dtos = extraction.getExtractedSkills().stream()
                        .map(skillName -> ExtractedSkillDto.builder()
                                .skillName(skillName)
                                .taxonomyRole(extraction.getDocumentType())
                                .evidenceSnippet(snippet)
                                .build())
                        .toList();
                entity.setExtractedSkills(objectMapper.writeValueAsString(dtos));
            } catch (Exception ex) {
                log.debug("Không thể tuần tự hóa extractedSkills: {}", ex.getMessage());
            }
        }
    }

    private static String firstNonBlankText(String primary, String fallback) {
        if (primary != null && !primary.isBlank() && !"null".equalsIgnoreCase(primary.trim())) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank() && !"null".equalsIgnoreCase(fallback.trim())) {
            return fallback.trim();
        }
        return null;
    }

    private void applyLookupEvaluation(
            SkillVerificationRequest req,
            Worker worker,
            Skill requestedSkill,
            CertificateLookupResult result,
            DocumentExtractionResult extractionResult
    ) {
        req.setVerificationSource(result.getVerificationSource());
        req.setVerificationReference(result.getVerificationReference());
        req.setRecipientName(firstNonBlankText(
                result.getRecipientName(),
                extractionResult != null ? extractionResult.getRecipientName() : null
        ));
        req.setInstitution(firstNonBlankText(
                result.getInstitution(),
                extractionResult != null ? extractionResult.getInstitution() : null
        ));
        req.setCertificateNumber(firstNonBlankText(
                result.getCertificateNumber(),
                extractionResult != null ? extractionResult.getCertificateNumber() : null
        ));
        req.setIssueDate(result.getIssueDate());
        req.setExpiryDate(firstNonBlankText(
                result.getExpiryDate(),
                extractionResult != null ? extractionResult.getExpiryDate() : null
        ));
        req.setHumanReviewed(false);

        if ((req.getModelName() == null || req.getModelName().isBlank() || "NONE".equalsIgnoreCase(req.getModelName()))
                && result.getVerificationSource() != null
                && !result.getVerificationSource().isBlank()
                && !UnsupportedCertificateLookupSource.SOURCE_CODE.equalsIgnoreCase(result.getVerificationSource())) {
            req.setModelName(result.getVerificationSource());
        }
        if ((req.getExtractedSkills() == null || req.getExtractedSkills().isBlank())
                && result.getMatchedSkillNames() != null
                && !result.getMatchedSkillNames().isEmpty()) {
            try {
                List<ExtractedSkillDto> dtos = result.getMatchedSkillNames().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(skillName -> ExtractedSkillDto.builder()
                                .skillName(skillName.trim())
                                .taxonomyRole("CERTIFICATE")
                                .evidenceSnippet(result.getVerificationSource())
                                .build())
                        .toList();
                if (!dtos.isEmpty()) {
                    req.setExtractedSkills(objectMapper.writeValueAsString(dtos));
                }
            } catch (Exception ex) {
                log.debug("Không thể tuần tự hóa matchedSkillNames từ nguồn tra cứu: {}", ex.getMessage());
            }
        }

        CertificateLookupOutcome outcome = result.getOutcome() != null
                ? result.getOutcome()
                : CertificateLookupOutcome.UNSUPPORTED_SOURCE;

        if (outcome == CertificateLookupOutcome.TEMPORARY_FAILURE) {
            req.setStatus(SkillVerificationStatus.VERIFICATION_FAILED);
            req.setRoutingDecision(RoutingDecision.REJECT);
            req.setFailureReason(result.getReason() != null && !result.getReason().isBlank()
                    ? result.getReason()
                    : "Nguồn tra cứu chứng chỉ đang gặp sự cố tạm thời. Vui lòng thử lại.");
            req.setRoutingReason(req.getFailureReason());
            req.setIssuerVerificationStatus(IssuerVerificationStatus.UNVERIFIED);
            req.setIdentityVerificationStatus(IdentityVerificationStatus.UNVERIFIED);
            req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.UNVERIFIED);
            req.setSpAwarded(0);
            return;
        }

        // Tài liệu hỏng / không đọc được -> ưu tiên báo lỗi tệp để Worker nộp lại
        if (extractionResult != null && extractionResult.isCorrupted()) {
            req.setStatus(SkillVerificationStatus.REJECTED);
            req.setRoutingDecision(RoutingDecision.REJECT);
            req.setFailureReason(null);
            req.setRoutingReason("Tài liệu tải lên bị hỏng hoặc không đọc được, chưa thể cấp điểm SP. Vui lòng nộp lại tệp chứng chỉ rõ nét.");
            req.setIssuerVerificationStatus(IssuerVerificationStatus.UNVERIFIED);
            req.setIdentityVerificationStatus(IdentityVerificationStatus.UNVERIFIED);
            req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.UNREADABLE);
            req.setSpAwarded(0);
            return;
        }

        // CV tự khai có thể chứa kỹ năng, nhưng bản thân CV chưa xác nhận chứng chỉ -> tuyệt đối không cấp SP
        if (extractionResult != null
                && "CV_SELF_DECLARED".equalsIgnoreCase(extractionResult.getDocumentType())) {
            req.setStatus(SkillVerificationStatus.REJECTED);
            req.setRoutingDecision(RoutingDecision.REJECT);
            req.setFailureReason(null);
            req.setRoutingReason("Tài liệu là CV tự khai (có thể chứa kỹ năng tự khai) nhưng bản thân CV chưa xác nhận chứng chỉ nên chưa thể cấp điểm SP. Vui lòng nộp lại chứng chỉ chính thức.");
            req.setIssuerVerificationStatus(IssuerVerificationStatus.UNVERIFIED);
            req.setIdentityVerificationStatus(IdentityVerificationStatus.UNVERIFIED);
            req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.UNVERIFIED);
            req.setSpAwarded(0);
            return;
        }

        if (outcome == CertificateLookupOutcome.UNSUPPORTED_SOURCE) {
            req.setStatus(SkillVerificationStatus.REJECTED);
            req.setRoutingDecision(RoutingDecision.REJECT);
            req.setFailureReason(null);
            String reason = (result.getReason() != null && !result.getReason().isBlank())
                    ? result.getReason()
                    : "Chưa thể xác minh tự động: CANi chưa hỗ trợ tra cứu chứng chỉ của đơn vị này.";
            if (!reason.contains("Chưa thể xác minh tự động")) {
                reason = "Chưa thể xác minh tự động: " + reason;
            }
            req.setRoutingReason(reason);
            req.setIssuerVerificationStatus(IssuerVerificationStatus.UNVERIFIED);
            req.setIdentityVerificationStatus(IdentityVerificationStatus.UNVERIFIED);
            req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.UNVERIFIED);
            req.setSpAwarded(0);
            return;
        }

        if (outcome == CertificateLookupOutcome.MISMATCH) {
            req.setStatus(SkillVerificationStatus.REJECTED);
            req.setRoutingDecision(RoutingDecision.REJECT);
            req.setFailureReason(null);
            req.setRoutingReason(result.getReason() != null && !result.getReason().isBlank()
                    ? result.getReason()
                    : "Thông tin từ nguồn tra cứu chứng chỉ không khớp với hồ sơ Worker.");
            req.setIssuerVerificationStatus(IssuerVerificationStatus.UNVERIFIED);
            req.setIdentityVerificationStatus(IdentityVerificationStatus.MISMATCH);
            req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.UNVERIFIED);
            req.setSpAwarded(0);
            return;
        }

        // VERIFIED từ nguồn tin cậy -> bắt buộc Worker đã xác thực danh tính eKYC trước khi đối soát
        Optional<String> verifiedEkycNameOpt = resolveVerifiedEkycFullName(worker);
        if (verifiedEkycNameOpt.isEmpty()) {
            req.setStatus(SkillVerificationStatus.REJECTED);
            req.setRoutingDecision(RoutingDecision.REJECT);
            req.setFailureReason(null);
            req.setRoutingReason("Thiếu danh tính eKYC đã xác thực: Worker cần hoàn tất eKYC trước khi đối soát người nhận chứng chỉ và nhận điểm SP.");
            req.setIssuerVerificationStatus(IssuerVerificationStatus.VERIFIED_ONLINE);
            req.setIdentityVerificationStatus(IdentityVerificationStatus.UNVERIFIED);
            req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.LIKELY_GENUINE_FORMAT);
            req.setSpAwarded(0);
            return;
        }

        String verifiedEkycName = verifiedEkycNameOpt.get();
        if (!isNameMatching(verifiedEkycName, result.getRecipientName())) {
            req.setStatus(SkillVerificationStatus.REJECTED);
            req.setRoutingDecision(RoutingDecision.REJECT);
            req.setFailureReason(null);
            req.setRoutingReason("Tên người nhận trên nguồn tra cứu chứng chỉ (" + result.getRecipientName()
                    + ") không khớp với danh tính eKYC đã xác thực (" + verifiedEkycName + ").");
            req.setIssuerVerificationStatus(IssuerVerificationStatus.VERIFIED_ONLINE);
            req.setIdentityVerificationStatus(IdentityVerificationStatus.MISMATCH);
            req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.LIKELY_GENUINE_FORMAT);
            req.setSpAwarded(0);
            return;
        }

        // Chứng chỉ vô thời hạn CHỈ hợp lệ khi có xác nhận rõ từ nguồn tra cứu chính thức (permanentExpiry = true)
        boolean isPermanent = result.isPermanent();
        LocalDate parsedExpiry = null;
        if (!isPermanent) {
            if (result.getExpiryDate() == null || result.getExpiryDate().isBlank()) {
                req.setStatus(SkillVerificationStatus.REJECTED);
                req.setRoutingDecision(RoutingDecision.REJECT);
                req.setFailureReason(null);
                req.setRoutingReason("Hạn hiệu lực từ nguồn tra cứu đang để trống trong khi nguồn chưa xác nhận chứng chỉ vô thời hạn (giữ 0 SP).");
                req.setIssuerVerificationStatus(IssuerVerificationStatus.VERIFIED_ONLINE);
                req.setIdentityVerificationStatus(IdentityVerificationStatus.EXACT_MATCH);
                req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.LIKELY_GENUINE_FORMAT);
                req.setSpAwarded(0);
                return;
            }
            if (isPermanentExpiry(result.getExpiryDate())) {
                req.setStatus(SkillVerificationStatus.REJECTED);
                req.setRoutingDecision(RoutingDecision.REJECT);
                req.setFailureReason(null);
                req.setRoutingReason("Chứng chỉ ghi vô thời hạn nhưng chưa có xác nhận rõ từ nguồn tra cứu chính thức (giữ 0 SP).");
                req.setIssuerVerificationStatus(IssuerVerificationStatus.VERIFIED_ONLINE);
                req.setIdentityVerificationStatus(IdentityVerificationStatus.EXACT_MATCH);
                req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.LIKELY_GENUINE_FORMAT);
                req.setSpAwarded(0);
                return;
            }
            parsedExpiry = parseFlexibleDate(result.getExpiryDate());
            if (parsedExpiry == null) {
                req.setStatus(SkillVerificationStatus.REJECTED);
                req.setRoutingDecision(RoutingDecision.REJECT);
                req.setFailureReason(null);
                req.setRoutingReason("Ngày hết hạn từ nguồn tra cứu chứng chỉ không đọc được hoặc sai định dạng theo chế độ STRICT ("
                        + result.getExpiryDate() + ").");
                req.setIssuerVerificationStatus(IssuerVerificationStatus.VERIFIED_ONLINE);
                req.setIdentityVerificationStatus(IdentityVerificationStatus.EXACT_MATCH);
                req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.LIKELY_GENUINE_FORMAT);
                req.setSpAwarded(0);
                return;
            }
        }

        if (parsedExpiry != null && parsedExpiry.isBefore(LocalDate.now(ZoneOffset.UTC))) {
            req.setStatus(SkillVerificationStatus.REJECTED);
            req.setRoutingDecision(RoutingDecision.REJECT);
            req.setFailureReason(null);
            req.setRoutingReason("Chứng chỉ đã hết hạn hiệu lực (" + result.getExpiryDate() + "). Vui lòng nộp chứng chỉ còn hiệu lực.");
            req.setIssuerVerificationStatus(IssuerVerificationStatus.VERIFIED_ONLINE);
            req.setIdentityVerificationStatus(IdentityVerificationStatus.EXACT_MATCH);
            req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.LIKELY_GENUINE_FORMAT);
            req.setSpAwarded(0);
            return;
        }

        List<Skill> skillsToApprove = resolveSkillsFromLookupOrRequest(requestedSkill, result);
        if (skillsToApprove.isEmpty()) {
            req.setStatus(SkillVerificationStatus.REJECTED);
            req.setRoutingDecision(RoutingDecision.REJECT);
            req.setFailureReason(null);
            if (result.getMatchedSkillNames() == null || result.getMatchedSkillNames().isEmpty()) {
                req.setRoutingReason("Nguồn tra cứu chứng chỉ không xác nhận kỹ năng nào trong chứng chỉ.");
            } else if (requestedSkill != null) {
                req.setRoutingReason("Kỹ năng được nguồn tra cứu xác nhận không khớp với kỹ năng yêu cầu thẩm định ("
                        + requestedSkill.getName() + ").");
            } else {
                req.setRoutingReason("Không xác định được kỹ năng hợp lệ trong danh mục CANi từ kết quả tra cứu chứng chỉ.");
            }
            req.setIssuerVerificationStatus(IssuerVerificationStatus.VERIFIED_ONLINE);
            req.setIdentityVerificationStatus(IdentityVerificationStatus.EXACT_MATCH);
            req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.LIKELY_GENUINE_FORMAT);
            req.setSpAwarded(0);
            return;
        }

        req.setSkill(skillsToApprove.get(0));
        req.setJobType(skillsToApprove.get(0).getName());
        req.setStatus(SkillVerificationStatus.APPROVED);
        req.setRoutingDecision(RoutingDecision.AUTO_APPROVE);
        req.setFailureReason(null);
        req.setHasSealOrSignature(true);
        req.setRoutingReason(result.getReason() != null && !result.getReason().isBlank()
                ? result.getReason()
                : "Chứng chỉ đã được xác minh tự động thành công qua nguồn tra cứu chính thức.");
        req.setIssuerVerificationStatus(IssuerVerificationStatus.VERIFIED_ONLINE);
        req.setIdentityVerificationStatus(IdentityVerificationStatus.EXACT_MATCH);
        req.setDocumentAuthenticityStatus(DocumentAuthenticityStatus.LIKELY_GENUINE_FORMAT);

        int baseSp = getAiDocumentSp();
        int spAwarded = Math.max(
                baseSp,
                skillsToApprove.size() * baseSp
        );
        req.setSpAwarded(spAwarded);
        applyApprovedSkills(worker, skillsToApprove, req.getJobType(), req.getId(), parsedExpiry);
    }

    private List<Skill> resolveSkillsFromLookupOrRequest(Skill requestedSkill, CertificateLookupResult result) {
        if (result == null || result.getMatchedSkillNames() == null || result.getMatchedSkillNames().isEmpty()) {
            // Nguồn chính thức KHÔNG xác nhận kỹ năng nào -> tuyệt đối không tự ý cấp điểm theo requestedSkill
            return Collections.emptyList();
        }

        List<Skill> matched = new ArrayList<>();
        if (requestedSkill != null && requestedSkill.getName() != null) {
            String normRequested = normalizeName(requestedSkill.getName());
            boolean confirmed = false;
            for (String sourceSkillName : result.getMatchedSkillNames()) {
                if (sourceSkillName == null || sourceSkillName.isBlank()) continue;
                String normSource = normalizeName(sourceSkillName);
                if (normSource.equals(normRequested)) {
                    confirmed = true;
                    break;
                }
                Optional<Skill> resolvedOpt = skillRepository.findByNameIgnoreCase(sourceSkillName.trim())
                        .or(() -> matchActiveSkillByAlias(sourceSkillName));
                if (resolvedOpt.isPresent() && (resolvedOpt.get().getSkillId().equals(requestedSkill.getSkillId())
                        || normalizeName(resolvedOpt.get().getName()).equals(normRequested))) {
                    confirmed = true;
                    break;
                }
            }
            if (confirmed) {
                matched.add(requestedSkill);
                return matched;
            }
            // Yêu cầu kỹ năng cụ thể nhưng nguồn tra cứu không xác nhận kỹ năng đó -> sai kỹ năng (0 SP)
            return Collections.emptyList();
        }

        // Không chọn kỹ năng trước -> lấy các kỹ năng do nguồn tra cứu xác nhận khớp với danh mục CANi
        for (String sourceSkillName : result.getMatchedSkillNames()) {
            if (sourceSkillName == null || sourceSkillName.isBlank()) continue;
            skillRepository.findByNameIgnoreCase(sourceSkillName.trim())
                    .or(() -> matchActiveSkillByAlias(sourceSkillName))
                    .ifPresent(matched::add);
        }
        return matched;
    }

    public static boolean isPermanentExpiry(String rawExpiry) {
        if (rawExpiry == null || rawExpiry.isBlank()) {
            return false;
        }
        String norm = normalizeName(rawExpiry).toUpperCase(Locale.ROOT);
        return norm.contains("VO THOI HAN")
                || norm.contains("KHONG THOI HAN")
                || norm.contains("KHONG CO THOI HAN")
                || norm.contains("LIFETIME")
                || norm.contains("PERMANENT")
                || norm.contains("NO EXPIRY")
                || norm.contains("UNLIMITED");
    }

    private static LocalDate parseFlexibleDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        String trimmed = rawDate.trim();
        for (DateTimeFormatter fmt : SUPPORTED_DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    /**
     * Tier 1: Worker Self-Declared Skills (UC-32 & UC-42).
     * No approval needed, SP_verify = 0 (BR-16 & BR-65).
     */
    @Transactional
    public List<WorkerSkillResponseDto> declareSkills(UUID workerId, DeclareSkillRequestDto request) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Worker với ID: " + workerId));

        if (request.getSkills() == null || request.getSkills().isEmpty()) {
            throw new BadRequestException("Danh sách kỹ năng khai báo không được để trống.");
        }

        List<WorkerSkill> resultList = new ArrayList<>();

        for (DeclareSkillRequestDto.SkillItem item : request.getSkills()) {
            Skill skill = skillRepository.findById(item.getSkillId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỹ năng: " + item.getSkillId()));
            if (Boolean.FALSE.equals(skill.getIsActive())) {
                throw new BadRequestException("Kỹ năng '" + skill.getName() + "' chưa được kích hoạt trong danh mục chuẩn.");
            }

            WorkerSkill ws = workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, skill.getSkillId())
                    .orElseGet(() -> WorkerSkill.builder()
                            .worker(worker)
                            .skill(skill)
                            .proficiency(item.getProficiency())
                            .verificationTier(VerificationTier.SELF_DECLARED)
                            .spVerifyAwarded(0)
                            .build()
                    );

            ws.setProficiency(item.getProficiency());

            if (ws.getVerificationTier() == null || ws.getVerificationTier() == VerificationTier.SELF_DECLARED) {
                ws.setVerificationTier(VerificationTier.SELF_DECLARED);
                ws.setSpVerifyAwarded(0);
            }

            WorkerSkill saved = workerSkillRepository.save(ws);
            resultList.add(saved);

            ensureWorkerSkillPoint(worker, skill);
        }

        return resultList.stream().map(this::mapToWorkerSkillDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WorkerSkillResponseDto> getWorkerSkills(UUID workerId) {
        List<WorkerSkill> list = workerSkillRepository.findByWorker_WorkerId(workerId);
        return list.stream().map(this::mapToWorkerSkillDto).collect(Collectors.toList());
    }

    public List<TierConfigDto> getTierConfigs() {
        return Arrays.stream(SkillTier.values())
                .map(t -> TierConfigDto.builder()
                        .tier(t)
                        .minSp(t.getMinSp())
                        .nextTierMinSp(t.next() != null ? t.next().getMinSp() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SkillVerificationResponseDto> getWorkerVerificationHistory(UUID workerId) {
        List<SkillVerificationRequest> list = verificationRequestRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(workerId);
        return list.stream().map(req -> mapToResponseDto(req, null, null, req.getWorker())).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SkillVerificationResponseDto getVerificationDetail(UUID verificationId, User currentUser) {
        if (currentUser == null) {
            throw new AppException("Vui lòng đăng nhập để xem chi tiết yêu cầu xác minh.", HttpStatus.UNAUTHORIZED);
        }
        SkillVerificationRequest req = verificationRequestRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thẩm định với ID: " + verificationId));

        boolean isPrivileged = currentUser.getRole() == UserRole.ADMIN
                || currentUser.getRole() == UserRole.CUSTOMER_CARE;

        if (!isPrivileged) {
            UUID ownerUserId = (req.getWorker() != null && req.getWorker().getUser() != null)
                    ? req.getWorker().getUser().getUserId()
                    : null;
            if (!currentUser.getUserId().equals(ownerUserId)) {
                throw new AppException("Bạn không có quyền xem hồ sơ xác minh của Worker khác.", HttpStatus.FORBIDDEN);
            }
        }

        return mapToResponseDto(req, null, null, req.getWorker());
    }

    @Transactional(readOnly = true)
    public List<WorkerSkillPointDto> getWorkerSkillPoints(UUID workerId) {
        List<WorkerSkillPoint> points = workerSkillPointRepository.findByWorker_WorkerId(workerId);
        return points.stream().map(p -> {
            UUID resolvedSkillId = p.getSkill() != null ? p.getSkill().getSkillId() : null;
            if (resolvedSkillId == null && p.getJobType() != null && !p.getJobType().isBlank()) {
                resolvedSkillId = skillRepository.findByNameIgnoreCase(p.getJobType().trim())
                        .map(Skill::getSkillId)
                        .orElse(null);
            }
            int ratingCount = p.getRatingCount() != null ? p.getRatingCount() : 0;
            int spRating = p.getSpRating() != null ? p.getSpRating() : 0;
            BigDecimal rawAvg = p.getAvgRating() != null ? p.getAvgRating() : BigDecimal.ZERO;
            BigDecimal safeAvg = (ratingCount == 0 && spRating == 0 && rawAvg.compareTo(new BigDecimal("5.00")) == 0)
                    ? BigDecimal.ZERO
                    : rawAvg;

            return WorkerSkillPointDto.builder()
                    .id(p.getSpId())
                    .skillId(resolvedSkillId)
                    .jobType(p.getJobType())
                    .spVerify(p.getSpVerify())
                    .spExperience(p.getSpExperience())
                    .spRating(p.getSpRating())
                    .totalSp(p.getTotalSp())
                    .tier(p.getTier())
                    .totalShiftsCompleted(p.getTotalShiftsCompleted())
                    .avgRating(safeAvg)
                    .ratingCount(ratingCount)
                    .updatedAt(p.getUpdatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SkillVerificationResponseDto> getAllVerificationsForAdmin(SkillVerificationStatus status) {
        List<SkillVerificationRequest> list = (status != null)
                ? verificationRequestRepository.findByStatusOrderByCreatedAtDesc(status)
                : verificationRequestRepository.findAllByOrderByCreatedAtDesc();

        return list.stream()
                .map(req -> mapToResponseDto(req, null, null, req.getWorker()))
                .collect(Collectors.toList());
    }

    /**
     * Admin Post-Audit Revocation (V2): Chuyển trạng thái sang REVOKED, hoàn tác toàn bộ WorkerSkill gắn với hồ sơ,
     * khấu trừ chính xác SP_verify, đồng bộ hạ Worker.skillTier và ghi AuditLog.
     */
    @Transactional
    public SkillVerificationResponseDto revokeVerification(UUID verificationId, UUID adminUserId, String reason) {
        SkillVerificationRequest req = verificationRequestRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thẩm định với ID: " + verificationId));

        if (req.getStatus() != SkillVerificationStatus.APPROVED) {
            throw new BadRequestException("Chỉ có thể thu hồi hồ sơ đang ở trạng thái APPROVED (hiện tại: " + req.getStatus() + ").");
        }

        Worker worker = req.getWorker();
        int spToDeduct = req.getSpAwarded() != null ? req.getSpAwarded() : getAiDocumentSp();
        Map<UUID, Skill> affectedSkills = new LinkedHashMap<>();
        Map<UUID, WorkerSkill> updatedBySkillId = new LinkedHashMap<>();
        Set<String> fallbackJobTypes = new LinkedHashSet<>();

        // Hoàn tác tất cả WorkerSkill được cấp từ yêu cầu này
        List<WorkerSkill> linkedSkills = new ArrayList<>(workerSkillRepository.findByEvidenceRequestId(req.getId()));
        if (linkedSkills.isEmpty() && req.getSkill() != null) {
            workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(worker.getWorkerId(), req.getSkill().getSkillId())
                    .ifPresent(linkedSkills::add);
        }

        for (WorkerSkill ws : linkedSkills) {
            if (ws.getSkill() != null && ws.getSkill().getSkillId() != null) {
                affectedSkills.put(ws.getSkill().getSkillId(), ws.getSkill());
            }
            if (ws.getVerificationTier() == VerificationTier.AI_DOCUMENT) {
                ws.setVerificationTier(VerificationTier.SELF_DECLARED);
                ws.setSpVerifyAwarded(0);
                ws.setVerifiedAt(null);
                ws.setExpiresAt(null);
                ws.setEvidenceRequestId(null);
                workerSkillRepository.save(ws);
            }
            if (ws.getSkill() != null && ws.getSkill().getSkillId() != null) {
                updatedBySkillId.put(ws.getSkill().getSkillId(), ws);
            }
        }

        if (req.getSkill() != null && req.getSkill().getSkillId() != null) {
            affectedSkills.putIfAbsent(req.getSkill().getSkillId(), req.getSkill());
        }
        if (affectedSkills.isEmpty() && req.getJobType() != null && !req.getJobType().isBlank()) {
            Optional<Skill> matched = skillRepository.findByNameIgnoreCase(req.getJobType().trim());
            if (matched.isPresent()) {
                affectedSkills.put(matched.get().getSkillId(), matched.get());
            } else {
                fallbackJobTypes.add(req.getJobType().trim());
            }
        }

        // Tính lại chính xác SP_verify theo từng Skill ID bị ảnh hưởng (không trừ nhầm toàn phần sang từng kỹ năng)
        for (Skill skill : affectedSkills.values()) {
            WorkerSkillPoint sp = findSkillPointForSkill(worker.getWorkerId(), skill).orElse(null);
            if (sp == null) {
                continue;
            }
            if (sp.getSkill() == null) {
                sp.setSkill(skill);
            }
            WorkerSkill currentWs = updatedBySkillId.get(skill.getSkillId());
            if (currentWs == null) {
                currentWs = workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(worker.getWorkerId(), skill.getSkillId())
                        .orElse(null);
            }
            int newVerify;
            if (currentWs != null) {
                int remaining = currentWs.getSpVerifyAwarded() != null ? currentWs.getSpVerifyAwarded() : 0;
                if (currentWs.getVerificationTier() == VerificationTier.PLATFORM_PROVEN) {
                    remaining = Math.max(remaining, getPlatformProvenSp());
                } else if (currentWs.getVerificationTier() == VerificationTier.SELF_DECLARED) {
                    remaining = 0;
                }
                newVerify = Math.max(0, remaining);
            } else {
                int currentVerify = sp.getSpVerify() != null ? sp.getSpVerify() : 0;
                int deductForSkill = Math.min(spToDeduct, getAiDocumentSp());
                newVerify = Math.max(0, currentVerify - deductForSkill);
            }

            int exp = sp.getSpExperience() != null ? sp.getSpExperience() : 0;
            int rating = sp.getSpRating() != null ? sp.getSpRating() : 0;
            sp.setSpVerify(newVerify);
            sp.setTotalSp(Math.max(0, newVerify + exp + rating));
            sp.setTier(SkillTier.fromTotalSp(sp.getTotalSp()));
            workerSkillPointRepository.save(sp);
        }

        for (String jobType : fallbackJobTypes) {
            workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), jobType)
                    .ifPresent(sp -> {
                        int currentVerify = sp.getSpVerify() != null ? sp.getSpVerify() : 0;
                        int newVerify = Math.max(0, currentVerify - spToDeduct);
                        int exp = sp.getSpExperience() != null ? sp.getSpExperience() : 0;
                        int rating = sp.getSpRating() != null ? sp.getSpRating() : 0;
                        sp.setSpVerify(newVerify);
                        sp.setTotalSp(Math.max(0, newVerify + exp + rating));
                        sp.setTier(SkillTier.fromTotalSp(sp.getTotalSp()));
                        workerSkillPointRepository.save(sp);
                    });
        }

        // Đồng bộ lại SkillTier tổng thể của Worker (có thể hạ hạng nếu điểm tụt)
        recomputeWorkerOverallTier(worker);

        req.setStatus(SkillVerificationStatus.REVOKED);
        req.setHumanReviewed(true);
        req.setReviewedBy(adminUserId);
        req.setReviewedAt(Instant.now());
        req.setReviewNotes("THU HỒI BỞI ADMIN: " + (reason != null && !reason.isBlank()
                ? reason.trim()
                : "Phát hiện chứng chỉ không hợp lệ"));
        req.setSpAwarded(0);
        SkillVerificationRequest saved = verificationRequestRepository.save(req);

        recordAudit(
                adminUserId,
                "SKILL_VERIFICATION_REVOKED",
                saved.getId(),
                Map.of("status", SkillVerificationStatus.APPROVED.name(), "spAwarded", spToDeduct),
                Map.of("status", SkillVerificationStatus.REVOKED.name(), "reason", saved.getReviewNotes())
        );

        log.info("Admin {} revoked skill verification {} for worker {}", adminUserId, verificationId, worker.getWorkerId());
        return mapToResponseDto(saved, null, null, worker);
    }

    /**
     * Tải tài liệu chứng chỉ có kiểm soát quyền sở hữu (Owner / ADMIN / CUSTOMER_CARE) hoặc Signed URL còn hạn.
     */
    @Transactional(readOnly = true)
    public Resource loadAuthorizedCertificateDocument(String filename, User currentUser, String expires, String sig) {
        if (filename == null || filename.isBlank()
                || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new BadRequestException("Tên tệp tài liệu không hợp lệ.");
        }

        boolean authorized = false;
        if (currentUser != null) {
            if (currentUser.getRole() == UserRole.ADMIN || currentUser.getRole() == UserRole.CUSTOMER_CARE) {
                authorized = true;
            } else {
                Optional<Worker> workerOpt = workerRepository.findByUser_UserId(currentUser.getUserId());
                if (workerOpt.isPresent()) {
                    String storedPath = DocumentAccessTokenService.DOCUMENT_BASE_PATH + filename;
                    authorized = verificationRequestRepository.existsByWorker_WorkerIdAndDocumentUrl(
                            workerOpt.get().getWorkerId(), storedPath);
                }
            }
        } else if (documentAccessTokenService.isValidSignature(filename, expires, sig)) {
            authorized = true;
        }

        if (!authorized) {
            throw new AppException("Bạn không có quyền truy cập tài liệu chứng chỉ này hoặc liên kết đã hết hạn.", HttpStatus.FORBIDDEN);
        }

        return certificateStorageService.loadAsResource(filename);
    }

    public static boolean isNameMatching(String workerName, String recipientName) {
        if (workerName == null || recipientName == null) {
            return false;
        }
        String normWorker = normalizeName(workerName);
        String normRecipient = normalizeName(recipientName);
        if (normWorker.equals(normRecipient)) {
            return true;
        }
        String[] wTokens = normWorker.split("\\s+");
        String[] rTokens = normRecipient.split("\\s+");
        if (wTokens.length >= 2 && rTokens.length >= 2) {
            boolean firstMatches = wTokens[0].equals(rTokens[0]);
            boolean lastMatches = wTokens[wTokens.length - 1].equals(rTokens[rTokens.length - 1]);
            if (firstMatches && lastMatches) {
                if (wTokens.length > 2 && rTokens.length > 2) {
                    List<String> wMid = Arrays.asList(wTokens).subList(1, wTokens.length - 1);
                    List<String> rMid = Arrays.asList(rTokens).subList(1, rTokens.length - 1);
                    return wMid.equals(rMid);
                }
                return true;
            }
        }
        return false;
    }

    public static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String nfd = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String noDiacritics = DIACRITICS.matcher(nfd).replaceAll("").replace('đ', 'd');
        return noDiacritics.replaceAll("[^a-z0-9\\s.]", " ").replaceAll("\\s+", " ").trim();
    }

    private void applyApprovedSkills(
            Worker worker,
            List<Skill> skills,
            String jobType,
            UUID requestId,
            LocalDate parsedExpiryDate
    ) {
        Instant now = Instant.now();
        Instant defaultExpiry = now.plus(730, ChronoUnit.DAYS); // BR-17: tối đa 24 tháng
        Instant effectiveExpiry = defaultExpiry;
        if (parsedExpiryDate != null) {
            Instant certExpiry = parsedExpiryDate.atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
            if (certExpiry.isBefore(defaultExpiry)) {
                effectiveExpiry = certExpiry;
            }
        }

        Map<UUID, Skill> managedSkillMap = new LinkedHashMap<>();
        Map<UUID, WorkerSkill> updatedWorkerSkills = new LinkedHashMap<>();

        for (Skill s : skills) {
            Skill managedSkill = (s.getSkillId() != null)
                    ? skillRepository.findById(s.getSkillId()).orElse(s)
                    : s;

            WorkerSkill ws = workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(worker.getWorkerId(), managedSkill.getSkillId())
                    .orElseGet(() -> WorkerSkill.builder()
                            .worker(worker)
                            .skill(managedSkill)
                            .build()
                    );

            int baseSp = getAiDocumentSp();
            // Không hạ cấp từ PLATFORM_PROVEN xuống AI_DOCUMENT
            if (ws.getVerificationTier() != VerificationTier.PLATFORM_PROVEN) {
                ws.setVerificationTier(VerificationTier.AI_DOCUMENT);
                ws.setSpVerifyAwarded(Math.max(
                        ws.getSpVerifyAwarded() != null ? ws.getSpVerifyAwarded() : 0,
                        baseSp
                ));
            }
            ws.setVerifiedAt(now);
            ws.setExpiresAt(effectiveExpiry);
            ws.setEvidenceRequestId(requestId);
            WorkerSkill savedWs = workerSkillRepository.save(ws);
            if (managedSkill.getSkillId() != null) {
                managedSkillMap.put(managedSkill.getSkillId(), managedSkill);
                updatedWorkerSkills.put(managedSkill.getSkillId(), savedWs);
            }
        }

        SkillTier highestSyncedTier = worker.getSkillTier() != null ? worker.getSkillTier() : SkillTier.STARTER;

        if (!managedSkillMap.isEmpty()) {
            for (Skill managedSkill : managedSkillMap.values()) {
                WorkerSkillPoint wsp = findOrCreateSkillPoint(worker, managedSkill);
                WorkerSkill wsForSkill = updatedWorkerSkills.get(managedSkill.getSkillId());
                int skillVerifySp = (wsForSkill != null && wsForSkill.getSpVerifyAwarded() != null)
                        ? wsForSkill.getSpVerifyAwarded()
                        : getAiDocumentSp();

                // Đồng bộ sp_verify theo đúng Skill ID (idempotent, không cộng dồn khi retry)
                wsp.setSpVerify(skillVerifySp);
                int exp = wsp.getSpExperience() != null ? wsp.getSpExperience() : 0;
                int rating = wsp.getSpRating() != null ? wsp.getSpRating() : 0;
                wsp.setTotalSp(skillVerifySp + exp + rating);

                SkillTier calculatedTier = SkillTier.fromTotalSp(wsp.getTotalSp());
                wsp.setTier(calculatedTier);
                workerSkillPointRepository.save(wsp);

                if (calculatedTier.ordinal() > highestSyncedTier.ordinal()) {
                    highestSyncedTier = calculatedTier;
                }
            }
        } else {
            String effectiveJobType = (jobType != null && !jobType.isBlank() && !DEFAULT_UNSPECIFIED_JOB_TYPE.equals(jobType))
                    ? jobType.trim()
                    : DEFAULT_UNSPECIFIED_JOB_TYPE;
            if (!DEFAULT_UNSPECIFIED_JOB_TYPE.equals(effectiveJobType)) {
                WorkerSkillPoint wsp = workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), effectiveJobType)
                        .orElseGet(() -> WorkerSkillPoint.builder()
                                .worker(worker)
                                .jobType(effectiveJobType)
                                .spVerify(0)
                                .spExperience(0)
                                .spRating(0)
                                .totalSp(0)
                                .tier(SkillTier.STARTER)
                                .avgRating(BigDecimal.ZERO)
                                .ratingCount(0)
                                .build()
                        );
                int totalSpVerifyForJob = Math.max(
                        wsp.getSpVerify() != null ? wsp.getSpVerify() : 0,
                        getAiDocumentSp()
                );
                wsp.setSpVerify(totalSpVerifyForJob);
                int exp = wsp.getSpExperience() != null ? wsp.getSpExperience() : 0;
                int rating = wsp.getSpRating() != null ? wsp.getSpRating() : 0;
                wsp.setTotalSp(totalSpVerifyForJob + exp + rating);
                SkillTier calculatedTier = SkillTier.fromTotalSp(wsp.getTotalSp());
                wsp.setTier(calculatedTier);
                workerSkillPointRepository.save(wsp);

                if (calculatedTier.ordinal() > highestSyncedTier.ordinal()) {
                    highestSyncedTier = calculatedTier;
                }
            }
        }

        if (worker.getSkillTier() == null || highestSyncedTier.ordinal() > worker.getSkillTier().ordinal()) {
            worker.setSkillTier(highestSyncedTier);
            workerRepository.save(worker);
        }

        log.info("Worker {} approved for {} skill(s), highest Tier: {}",
                worker.getWorkerId(), managedSkillMap.size(), highestSyncedTier);
    }

    private void recomputeWorkerOverallTier(Worker worker) {
        List<WorkerSkillPoint> allPoints = workerSkillPointRepository.findByWorker_WorkerId(worker.getWorkerId());
        SkillTier highest = SkillTier.STARTER;
        for (WorkerSkillPoint p : allPoints) {
            if (p.getTier() != null && p.getTier().ordinal() > highest.ordinal()) {
                highest = p.getTier();
            }
        }
        worker.setSkillTier(highest);
        workerRepository.save(worker);
    }

    private Optional<WorkerSkillPoint> findSkillPointForSkill(UUID workerId, Skill skill) {
        if (workerId == null || skill == null) {
            return Optional.empty();
        }
        if (skill.getSkillId() != null) {
            Optional<WorkerSkillPoint> bySkillId = workerSkillPointRepository
                    .findByWorker_WorkerIdAndSkill_SkillId(workerId, skill.getSkillId());
            if (bySkillId.isPresent()) {
                return bySkillId;
            }
        }
        if (skill.getName() != null && !skill.getName().isBlank()) {
            Optional<WorkerSkillPoint> byName = workerSkillPointRepository
                    .findByWorker_WorkerIdAndJobType(workerId, skill.getName());
            if (byName.isEmpty()) {
                byName = workerSkillPointRepository.findFirstByWorker_WorkerIdAndJobTypeIgnoreCase(workerId, skill.getName().trim());
            }
            if (byName.isPresent()) {
                WorkerSkillPoint found = byName.get();
                if (found.getSkill() == null) {
                    found.setSkill(skill);
                }
                return Optional.of(found);
            }
        }
        return Optional.empty();
    }

    private WorkerSkillPoint findOrCreateSkillPoint(Worker worker, Skill skill) {
        return findSkillPointForSkill(worker.getWorkerId(), skill)
                .orElseGet(() -> WorkerSkillPoint.builder()
                        .worker(worker)
                        .skill(skill)
                        .jobType(skill.getName())
                        .spVerify(0)
                        .spExperience(0)
                        .spRating(0)
                        .totalSp(0)
                        .tier(SkillTier.STARTER)
                        .totalShiftsCompleted(0)
                        .totalShiftsCancelled(0)
                        .avgRating(BigDecimal.ZERO)
                        .ratingCount(0)
                        .build()
                );
    }

    private void ensureWorkerSkillPoint(Worker worker, Skill skill) {
        if (worker == null || skill == null || skill.getName() == null || skill.getName().isBlank()) {
            return;
        }
        Optional<WorkerSkillPoint> existing = findSkillPointForSkill(worker.getWorkerId(), skill);
        if (existing.isPresent()) {
            WorkerSkillPoint wsp = existing.get();
            if (wsp.getSkill() == null) {
                wsp.setSkill(skill);
                workerSkillPointRepository.save(wsp);
            }
            return;
        }
        workerSkillPointRepository.save(WorkerSkillPoint.builder()
                .worker(worker)
                .skill(skill)
                .jobType(skill.getName())
                .spVerify(0)
                .spExperience(0)
                .spRating(0)
                .totalSp(0)
                .tier(SkillTier.STARTER)
                .totalShiftsCompleted(0)
                .totalShiftsCancelled(0)
                .avgRating(BigDecimal.ZERO)
                .ratingCount(0)
                .build());
    }

    private Optional<String> resolveVerifiedEkycFullName(Worker worker) {
        if (worker == null) {
            return Optional.empty();
        }
        if (workerEkycVerificationRepository != null) {
            try {
                Optional<String> fromHistory = workerEkycVerificationRepository
                        .findTopByWorker_WorkerIdAndStatusInOrderByCreatedAtDesc(
                                worker.getWorkerId(), List.of("VERIFIED", "APPROVED"))
                        .map(WorkerEkycVerification::getFullName)
                        .map(String::trim)
                        .filter(name -> !name.isBlank());
                if (fromHistory.isPresent()) {
                    return fromHistory;
                }
            } catch (Exception ex) {
                log.warn("Không thể lấy danh tính eKYC đã xác thực của Worker {}: {}", worker.getWorkerId(), ex.getMessage());
            }
        }
        if (worker.getKycStatus() == com.cani.entity.enums.KycStatus.APPROVED) {
            String workerFullName = firstNonBlankText(
                    worker.getFullName(),
                    worker.getUser() != null ? worker.getUser().getFullName() : null
            );
            return Optional.ofNullable(workerFullName);
        }
        return Optional.empty();
    }

    private WorkerSkillResponseDto mapToWorkerSkillDto(WorkerSkill ws) {
        boolean expired = ws.getExpiresAt() != null && ws.getExpiresAt().isBefore(Instant.now());
        return WorkerSkillResponseDto.builder()
                .id(ws.getId())
                .skillId(ws.getSkill() != null ? ws.getSkill().getSkillId() : null)
                .skillName(ws.getSkill() != null ? ws.getSkill().getName() : null)
                .category(ws.getSkill() != null ? ws.getSkill().getCategory() : null)
                .proficiency(ws.getProficiency())
                .verificationTier(ws.getVerificationTier())
                .spVerifyAwarded(ws.getSpVerifyAwarded())
                .verifiedAt(ws.getVerifiedAt())
                .expiresAt(ws.getExpiresAt())
                .isExpired(expired)
                .build();
    }

    private SkillVerificationResponseDto mapToResponseDto(
            SkillVerificationRequest req,
            List<ExtractedSkillDto> skillsOverride,
            TrustScoreBreakdownDto breakdownOverride,
            Worker worker
    ) {
        List<ExtractedSkillDto> skills = skillsOverride;
        if (skills == null && req.getExtractedSkills() != null) {
            try {
                ExtractedSkillDto[] arr = objectMapper.readValue(req.getExtractedSkills(), ExtractedSkillDto[].class);
                skills = Arrays.asList(arr);
            } catch (Exception e) {
                skills = Collections.emptyList();
            }
        }

        TrustScoreBreakdownDto breakdown = breakdownOverride;
        if (breakdown == null && req.getTrustScoreBreakdown() != null) {
            try {
                breakdown = objectMapper.readValue(req.getTrustScoreBreakdown(), TrustScoreBreakdownDto.class);
            } catch (Exception e) {
                breakdown = null;
            }
        }

        int totalSp = 0;
        if (worker != null && req.getJobType() != null) {
            totalSp = workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), req.getJobType())
                    .map(WorkerSkillPoint::getTotalSp)
                    .orElse(0);
        }

        Worker w = worker != null ? worker : req.getWorker();
        String workerName = (w != null && w.getFullName() != null && !w.getFullName().isBlank())
                ? w.getFullName()
                : ((w != null && w.getUser() != null) ? w.getUser().getFullName() : null);

        String signedDocUrl = documentAccessTokenService != null
                ? documentAccessTokenService.generateSignedDocumentUrl(req.getDocumentUrl())
                : req.getDocumentUrl();

        return SkillVerificationResponseDto.builder()
                .id(req.getId())
                .workerId(req.getWorker().getWorkerId())
                .workerName(workerName)
                .documentUrl(signedDocUrl)
                .skillId(req.getSkill() != null ? req.getSkill().getSkillId() : null)
                .skillName(req.getSkill() != null ? req.getSkill().getName() : req.getJobType())
                .jobType(req.getJobType())
                .recipientName(req.getRecipientName())
                .identityNumber(maskSensitiveId(req.getIdentityNumber()))
                .institution(req.getInstitution())
                .certificateNumber(req.getCertificateNumber())
                .issueDate(req.getIssueDate())
                .expiryDate(req.getExpiryDate())
                .extractedSkills(skills)
                .hasSealOrSignature(req.getHasSealOrSignature())
                .overallConfidence(req.getOverallConfidence() != null ? req.getOverallConfidence().doubleValue() : 0.0)
                .trustScore(req.getTrustScore() != null ? req.getTrustScore().doubleValue() : null)
                .trustScoreBreakdown(breakdown)
                .identityVerificationStatus(req.getIdentityVerificationStatus())
                .issuerVerificationStatus(req.getIssuerVerificationStatus())
                .documentAuthenticityStatus(req.getDocumentAuthenticityStatus())
                .verificationSource(req.getVerificationSource())
                .verificationReference(req.getVerificationReference())
                .modelName(req.getModelName())
                .promptVersion(req.getPromptVersion())
                .humanReviewed(req.getHumanReviewed())
                .reviewedBy(req.getReviewedBy())
                .reviewedAt(req.getReviewedAt())
                .reviewNotes(req.getReviewNotes())
                .failureCode(resolveFailureCode(req))
                .failureReason(req.getFailureReason())
                .retryCount(req.getRetryCount())
                .routingDecision(req.getRoutingDecision())
                .routingReason(req.getRoutingReason())
                .status(req.getStatus())
                .spAwarded(req.getSpAwarded())
                .currentTotalSp(totalSp)
                .currentTier(w != null && w.getSkillTier() != null ? w.getSkillTier() : SkillTier.STARTER)
                .createdAt(req.getCreatedAt())
                .build();
    }

    private String resolveFailureCode(SkillVerificationRequest req) {
        if (req == null || req.getStatus() == null) {
            return null;
        }
        SkillVerificationStatus status = req.getStatus();
        if (status == SkillVerificationStatus.APPROVED || status == SkillVerificationStatus.REVOKED) {
            return null;
        }
        if (status == SkillVerificationStatus.VERIFICATION_FAILED) {
            return "TEMPORARY_FAILURE";
        }
        if (req.getDocumentAuthenticityStatus() == DocumentAuthenticityStatus.UNREADABLE) {
            return "CORRUPTED_OR_UNREADABLE";
        }
        String routingReason = req.getRoutingReason() != null ? req.getRoutingReason() : "";
        if (routingReason.contains("CV tự khai") || routingReason.contains("CV_SELF_DECLARED")) {
            return "CV_SELF_DECLARED";
        }
        if (req.getIdentityVerificationStatus() == IdentityVerificationStatus.UNVERIFIED
                && req.getIssuerVerificationStatus() == IssuerVerificationStatus.VERIFIED_ONLINE) {
            return "EKYC_REQUIRED";
        }
        if (req.getIdentityVerificationStatus() == IdentityVerificationStatus.MISMATCH) {
            return "IDENTITY_MISMATCH";
        }
        if (UnsupportedCertificateLookupSource.SOURCE_CODE.equalsIgnoreCase(req.getVerificationSource())) {
            return "UNSUPPORTED_SOURCE";
        }
        return "VERIFICATION_REJECTED";
    }

    private String maskSensitiveId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }

    private String serializeQuietly(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Không thể serialize dữ liệu sang JSON: {}", e.getMessage());
            return null;
        }
    }

    private void recordAudit(UUID actorUserId, String action, UUID entityId, Object oldVal, Object newVal) {
        if (auditLogService == null) {
            return;
        }
        auditLogService.logAction(AuditLog.builder()
                .userId(actorUserId)
                .action(action)
                .entityType(AUDIT_ENTITY_TYPE)
                .entityId(entityId)
                .oldValues(serializeQuietly(oldVal))
                .newValues(serializeQuietly(newVal))
                .build());
    }

    @Transactional(readOnly = true)
    public List<Skill> getTaxonomy() {
        return skillRepository.findByIsActiveTrue();
    }

    @Transactional
    public Skill getOrCreateCustomSkill(String rawName, String rawCategory) {
        if (rawName == null || rawName.trim().isEmpty()) {
            throw new BadRequestException("Tên kỹ năng không được để trống.");
        }
        String trimmedName = rawName.trim();
        if (trimmedName.length() > 100) {
            throw new BadRequestException("Tên kỹ năng không được vượt quá 100 ký tự.");
        }

        Optional<Skill> existing = skillRepository.findByNameIgnoreCase(trimmedName);
        if (existing.isPresent()) {
            return existing.get();
        }

        Optional<Skill> matchedTaxonomy = matchActiveSkillByAlias(trimmedName);
        if (matchedTaxonomy.isPresent()) {
            return matchedTaxonomy.get();
        }

        String category = (rawCategory != null && !rawCategory.trim().isEmpty())
                ? rawCategory.trim()
                : "Đề xuất";

        // Không tự tạo Skill active tràn lan vào taxonomy chính; lưu ở trạng thái đề xuất (is_active = false)
        Skill proposalSkill = Skill.builder()
                .name(trimmedName)
                .category(category)
                .description("Đề xuất kỹ năng mới do EO/Worker nhập - chờ chuẩn hóa vào danh mục")
                .isActive(false)
                .build();

        return skillRepository.save(proposalSkill);
    }

    private Optional<Skill> matchActiveSkillByAlias(String rawName) {
        String normalizedInput = normalizeName(rawName);
        if (normalizedInput.isBlank()) {
            return Optional.empty();
        }
        List<Skill> activeSkills = skillRepository.findByIsActiveTrue();
        if (activeSkills == null || activeSkills.isEmpty()) {
            return Optional.empty();
        }

        for (Skill skill : activeSkills) {
            if (skill.getName() == null) {
                continue;
            }
            String normSkill = normalizeName(skill.getName());
            if (normSkill.equals(normalizedInput)) {
                return Optional.of(skill);
            }
        }

        // Alias chuẩn cho các tên gọi phổ biến tại Việt Nam -> ánh xạ về Skill chuẩn đang active
        Map<String, String> aliasHints = Map.ofEntries(
                Map.entry("waiter", "phuc vu ban"),
                Map.entry("waitress", "phuc vu ban"),
                Map.entry("chay ban", "phuc vu ban"),
                Map.entry("phuc vu", "phuc vu ban"),
                Map.entry("bartender", "pha che"),
                Map.entry("barista", "pha che"),
                Map.entry("pha che", "pha che"),
                Map.entry("check in", "le tan"),
                Map.entry("checkin", "le tan"),
                Map.entry("le tan", "le tan"),
                Map.entry("cashier", "thu ngan"),
                Map.entry("thu ngan", "thu ngan"),
                Map.entry("security", "bao ve"),
                Map.entry("bao ve", "bao ve"),
                Map.entry("setup", "hau can"),
                Map.entry("hau can", "hau can"),
                Map.entry("logistics", "hau can"),
                Map.entry("coordinator", "dieu phoi su kien"),
                Map.entry("dieu phoi", "dieu phoi su kien"),
                Map.entry("sound", "ky thuat am thanh"),
                Map.entry("lighting", "ky thuat am thanh"),
                Map.entry("am thanh", "ky thuat am thanh"),
                Map.entry("anh sang", "ky thuat am thanh"),
                Map.entry("phu bep", "phu bep"),
                Map.entry("pg", "pg / pb"),
                Map.entry("pb", "pg / pb")
        );

        String targetHint = null;
        for (Map.Entry<String, String> entry : aliasHints.entrySet()) {
            if (normalizedInput.equals(entry.getKey()) || normalizedInput.contains(entry.getKey())) {
                targetHint = entry.getValue();
                break;
            }
        }
        if (targetHint != null) {
            final String hint = targetHint;
            return activeSkills.stream()
                    .filter(s -> s.getName() != null && normalizeName(s.getName()).contains(hint))
                    .findFirst();
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public UUID resolveWorkerId(User user, UUID explicitWorkerId) {
        if (user == null) {
            throw new BadRequestException("Vui lòng đăng nhập để thực hiện thao tác này.");
        }
        if (explicitWorkerId != null && user.getRole() == UserRole.ADMIN) {
            return explicitWorkerId;
        }
        Worker worker = workerRepository.findByUser_UserId(user.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng chưa có hồ sơ Worker trên hệ thống"));
        if (explicitWorkerId != null && !worker.getWorkerId().equals(explicitWorkerId)) {
            throw new AppException("Không có quyền thao tác thay cho Worker khác", HttpStatus.FORBIDDEN);
        }
        return worker.getWorkerId();
    }
}
