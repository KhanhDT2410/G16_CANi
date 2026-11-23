package com.cani.service;

import com.cani.client.skill.AiProviderException;
import com.cani.client.skill.SkillVerificationPromptBuilder;
import com.cani.client.skill.SkillVisionAiClient;
import com.cani.dto.skill.*;
import com.cani.entity.AuditLog;
import com.cani.entity.Skill;
import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerSkill;
import com.cani.entity.WorkerSkillPoint;
import com.cani.entity.enums.AdminReviewDecision;
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
import com.cani.repository.WorkerRepository;
import com.cani.repository.WorkerSkillPointRepository;
import com.cani.repository.WorkerSkillRepository;
import com.cani.service.skill.CertificateStorageService;
import com.cani.service.skill.DocumentAccessTokenService;
import com.cani.service.skill.FileSecurityValidator;
import com.cani.service.skill.SkillTrustScoringEngine;
import com.cani.service.skill.TrustEvaluationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Orchestrator cho module CANi Skill Trust & Verification Engine (V2).
 * Phân tách rõ:
 * - FileSecurityValidator + CertificateStorageService + DocumentAccessTokenService (Bảo mật tệp)
 * - SkillVisionAiClient (Document Intelligence - Fail-Closed)
 * - SkillTrustScoringEngine (TrustScore + Hard Gates - Backend quyết định cuối cùng)
 * - AuditLogService (Kiểm toán độc lập REQUIRES_NEW)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillVerificationService {

    private static final String DEFAULT_UNSPECIFIED_JOB_TYPE = "Chưa xác định";
    private static final int MAX_AI_RETRY_COUNT = 3;
    private static final String AUDIT_ENTITY_TYPE = "SKILL_VERIFICATION_REQUEST";
    private static final List<SkillVerificationStatus> ACTIVE_DUPLICATE_STATUSES = List.of(
            SkillVerificationStatus.APPROVED,
            SkillVerificationStatus.PENDING_REVIEW
    );

    private final WorkerRepository workerRepository;
    private final SkillRepository skillRepository;
    private final WorkerSkillRepository workerSkillRepository;
    private final WorkerSkillPointRepository workerSkillPointRepository;
    private final SkillVerificationRequestRepository verificationRequestRepository;
    private final SkillVisionAiClient skillVisionAiClient;
    private final ObjectMapper objectMapper;
    private final FileSecurityValidator fileSecurityValidator;
    private final CertificateStorageService certificateStorageService;
    private final DocumentAccessTokenService documentAccessTokenService;
    private final SkillTrustScoringEngine trustScoringEngine;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    private <T> T executeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        }
        return action.get();
    }

    /**
     * Submit certificate/credential for V2 Trust & Verification Pipeline (UC-43 & UC-46).
     * NOTE: Outer method is intentionally NOT @Transactional to avoid holding DB connections during Gemini HTTP I/O.
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

        // 2. Chống nộp trùng tài liệu đang chờ duyệt hoặc đã được phê duyệt
        if (verificationRequestRepository.existsByWorker_WorkerIdAndDocumentHashAndStatusIn(
                workerId, validatedDoc.getSha256Hash(), ACTIVE_DUPLICATE_STATUSES)) {
            throw new AppException(
                    "Tài liệu chứng chỉ này đã được nộp trước đó và đang chờ duyệt hoặc đã được phê duyệt.",
                    HttpStatus.CONFLICT
            );
        }

        Skill requestedSkill = requestedSkillId != null
                ? skillRepository.findById(requestedSkillId).orElse(null)
                : null;

        List<Skill> activeSkills = skillRepository.findByIsActiveTrue();
        List<String> taxonomyNames = activeSkills.stream().map(Skill::getName).toList();

        // 3. Lưu tệp riêng tư bằng UUID filename (fail-fast nếu lỗi ghi đĩa)
        String storedDocumentUrl = certificateStorageService.storeDocument(
                validatedDoc.getBytes(),
                validatedDoc.getSafeExtension()
        );

        String workerFullName = resolveWorkerFullName(worker);

        // 4. Gọi tầng Document Intelligence (truyền đầy đủ expectedWorkerName & sanitized filename)
        try {
            SkillVerificationAiResultDto aiResult = skillVisionAiClient.verifyDocument(
                    validatedDoc.getBytes(),
                    validatedDoc.getDetectedMimeType(),
                    taxonomyNames,
                    validatedDoc.getSanitizedOriginalFilename(),
                    workerFullName
            );

            // 5. Backend Trust Scoring Engine tính TrustScore + Hard Gates -> quyết định cuối cùng
            TrustEvaluationResult evaluation = trustScoringEngine.evaluate(
                    workerFullName, requestedSkill, activeSkills, aiResult
            );

            return executeInTransaction(() -> persistTrustVerificationResult(
                    workerId,
                    requestedSkill,
                    validatedDoc,
                    storedDocumentUrl,
                    aiResult,
                    evaluation,
                    notes,
                    activeSkills
            ));
        } catch (AiProviderException ex) {
            // Fail-Closed: tuyệt đối KHÔNG tự duyệt hay bịa dữ liệu khi AI lỗi
            log.warn("Skill verification fail-closed for workerId={} [code={}, retryable={}]",
                    workerId, ex.getErrorCode(), ex.isRetryable());
            return executeInTransaction(() -> persistFailedVerification(
                    workerId,
                    requestedSkill,
                    validatedDoc,
                    storedDocumentUrl,
                    ex,
                    notes
            ));
        }
    }

    /**
     * Worker bổ sung / thay thế tài liệu khi yêu cầu ở trạng thái NEEDS_MORE_INFO hoặc VERIFICATION_FAILED.
     */
    public SkillVerificationResponseDto resubmitVerification(
            UUID verificationId,
            UUID workerId,
            MultipartFile file,
            String notes
    ) {
        record ResubmitContext(Skill skill, String workerFullName) {}

        ResubmitContext ctx = executeInTransaction(() -> {
            SkillVerificationRequest existing = verificationRequestRepository.findById(verificationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thẩm định với ID: " + verificationId));

            if (!existing.getWorker().getWorkerId().equals(workerId)) {
                throw new AppException("Bạn không có quyền bổ sung tài liệu cho hồ sơ của Worker khác.", HttpStatus.FORBIDDEN);
            }

            if (existing.getStatus() != SkillVerificationStatus.NEEDS_MORE_INFO
                    && existing.getStatus() != SkillVerificationStatus.VERIFICATION_FAILED
                    && existing.getStatus() != SkillVerificationStatus.REJECTED) {
                throw new BadRequestException("Chỉ có thể bổ sung tài liệu khi hồ sơ ở trạng thái NEEDS_MORE_INFO, VERIFICATION_FAILED hoặc REJECTED.");
            }
            return new ResubmitContext(existing.getSkill(), resolveWorkerFullName(existing.getWorker()));
        });

        FileSecurityValidator.ValidatedDocument validatedDoc = fileSecurityValidator.validate(file);

        // Không cho phép nộp lại y hệt tệp cũ đang bị từ chối/lỗi nếu đã có bản APPROVED/PENDING_REVIEW
        if (verificationRequestRepository.existsByWorker_WorkerIdAndDocumentHashAndStatusIn(
                workerId, validatedDoc.getSha256Hash(), ACTIVE_DUPLICATE_STATUSES)) {
            throw new AppException("Tài liệu này đã tồn tại trong một hồ sơ đang chờ duyệt hoặc đã phê duyệt.", HttpStatus.CONFLICT);
        }

        List<Skill> activeSkills = skillRepository.findByIsActiveTrue();
        List<String> taxonomyNames = activeSkills.stream().map(Skill::getName).toList();
        String storedDocumentUrl = certificateStorageService.storeDocument(
                validatedDoc.getBytes(),
                validatedDoc.getSafeExtension()
        );

        try {
            SkillVerificationAiResultDto aiResult = skillVisionAiClient.verifyDocument(
                    validatedDoc.getBytes(),
                    validatedDoc.getDetectedMimeType(),
                    taxonomyNames,
                    validatedDoc.getSanitizedOriginalFilename(),
                    ctx.workerFullName()
            );
            TrustEvaluationResult evaluation = trustScoringEngine.evaluate(
                    ctx.workerFullName(), ctx.skill(), activeSkills, aiResult
            );
            return executeInTransaction(() -> updateResubmittedRequest(
                    verificationId, validatedDoc, storedDocumentUrl, aiResult, evaluation, notes, activeSkills
            ));
        } catch (AiProviderException ex) {
            log.warn("Skill verification resubmit fail-closed [requestId={}, code={}]", verificationId, ex.getErrorCode());
            return executeInTransaction(() -> updateFailedRequest(verificationId, validatedDoc, storedDocumentUrl, ex, notes, false));
        }
    }

    /**
     * Admin/Hệ thống thực hiện retry có kiểm soát cho yêu cầu bị VERIFICATION_FAILED (tối đa 3 lần).
     */
    public SkillVerificationResponseDto retryFailedVerification(UUID verificationId, UUID adminUserId) {
        record RetryContext(
                String documentUrl,
                String documentName,
                String documentType,
                String workerNotes,
                int currentRetries,
                Skill skill,
                String workerFullName
        ) {}

        RetryContext ctx = executeInTransaction(() -> {
            SkillVerificationRequest existing = verificationRequestRepository.findById(verificationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thẩm định với ID: " + verificationId));

            if (existing.getStatus() != SkillVerificationStatus.VERIFICATION_FAILED) {
                throw new BadRequestException("Chỉ có thể thử lại (retry) hồ sơ ở trạng thái VERIFICATION_FAILED.");
            }

            int currentRetries = existing.getRetryCount() != null ? existing.getRetryCount() : 0;
            if (currentRetries >= MAX_AI_RETRY_COUNT) {
                throw new BadRequestException("Hồ sơ đã vượt quá giới hạn " + MAX_AI_RETRY_COUNT
                        + " lần thử lại AI. Vui lòng kiểm tra thủ công hoặc yêu cầu Worker nộp lại tài liệu.");
            }
            return new RetryContext(
                    existing.getDocumentUrl(),
                    existing.getDocumentName(),
                    existing.getDocumentType(),
                    existing.getWorkerNotes(),
                    currentRetries,
                    existing.getSkill(),
                    resolveWorkerFullName(existing.getWorker())
            );
        });

        String storedFilename = documentAccessTokenService.extractFilename(ctx.documentUrl());
        if (storedFilename == null) {
            throw new BadRequestException("Không tìm thấy tệp chứng chỉ lưu trữ nội bộ để thực hiện thử lại.");
        }

        byte[] fileBytes = certificateStorageService.readBytes(storedFilename);
        String mimeType = ctx.documentType() != null ? ctx.documentType() : "image/jpeg";
        List<Skill> activeSkills = skillRepository.findByIsActiveTrue();
        List<String> taxonomyNames = activeSkills.stream().map(Skill::getName).toList();

        try {
            SkillVerificationAiResultDto aiResult = skillVisionAiClient.verifyDocument(
                    fileBytes,
                    mimeType,
                    taxonomyNames,
                    ctx.documentName(),
                    ctx.workerFullName()
            );
            TrustEvaluationResult evaluation = trustScoringEngine.evaluate(
                    ctx.workerFullName(), ctx.skill(), activeSkills, aiResult
            );
            return executeInTransaction(() -> applyRetrySuccess(verificationId, adminUserId, aiResult, evaluation, activeSkills));
        } catch (AiProviderException ex) {
            log.warn("Controlled retry failed [requestId={}, attempt={}, code={}]",
                    verificationId, ctx.currentRetries() + 1, ex.getErrorCode());
            return executeInTransaction(() -> updateFailedRequest(verificationId, null, ctx.documentUrl(), ex, ctx.workerNotes(), true));
        }
    }

    @Transactional
    protected SkillVerificationResponseDto persistTrustVerificationResult(
            UUID workerId,
            Skill requestedSkill,
            FileSecurityValidator.ValidatedDocument validatedDoc,
            String storedDocumentUrl,
            SkillVerificationAiResultDto aiResult,
            TrustEvaluationResult evaluation,
            String notes,
            List<Skill> activeSkills
    ) {
        // Kiểm tra lại chống race condition trong transaction
        if (verificationRequestRepository.existsByWorker_WorkerIdAndDocumentHashAndStatusIn(
                workerId, validatedDoc.getSha256Hash(), ACTIVE_DUPLICATE_STATUSES)) {
            throw new AppException(
                    "Tài liệu chứng chỉ này đang được xử lý hoặc đã được phê duyệt trước đó.",
                    HttpStatus.CONFLICT
            );
        }

        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + workerId));

        List<Skill> approvedSkills = evaluation.getFinalStatus() == SkillVerificationStatus.APPROVED
                ? resolveSkillsForApproval(evaluation.getMatchedApprovedSkills(), requestedSkill)
                : Collections.emptyList();

        int spAwarded = evaluation.getFinalStatus() == SkillVerificationStatus.APPROVED
                ? Math.max(VerificationTier.AI_DOCUMENT.getSpVerify(),
                approvedSkills.size() * VerificationTier.AI_DOCUMENT.getSpVerify())
                : 0;

        String resolvedJobType = resolveJobType(aiResult, requestedSkill, approvedSkills);
        Skill rawResolvedSkill = resolveLinkedSkill(requestedSkill, approvedSkills, aiResult, activeSkills);
        Skill resolvedSkill = (rawResolvedSkill != null && rawResolvedSkill.getSkillId() != null)
                ? skillRepository.findById(rawResolvedSkill.getSkillId()).orElse(rawResolvedSkill)
                : rawResolvedSkill;

        SkillVerificationRequest entity = SkillVerificationRequest.builder()
                .worker(worker)
                .skill(resolvedSkill)
                .jobType(resolvedJobType)
                .documentUrl(storedDocumentUrl)
                .documentName(validatedDoc.getSanitizedOriginalFilename())
                .documentType(validatedDoc.getDetectedMimeType())
                .documentHash(validatedDoc.getSha256Hash())
                .recipientName(aiResult.getRecipientName())
                .identityNumber(aiResult.getIdentityNumber())
                .institution(aiResult.getInstitution())
                .certificateNumber(aiResult.getCertificateNumber())
                .issueDate(aiResult.getIssueDate())
                .expiryDate(aiResult.getExpiryDate())
                .modelName(aiResult.getModelName())
                .promptVersion(aiResult.getPromptVersion() != null
                        ? aiResult.getPromptVersion()
                        : SkillVerificationPromptBuilder.PROMPT_VERSION)
                .aiRawResponse(sanitizeJsonOrNull(aiResult.getRawAiJson()))
                .extractedSkills(serializeQuietly(aiResult.getExtractedSkills()))
                .hasSealOrSignature(Boolean.TRUE.equals(aiResult.getHasSealOrSignature())
                        || Boolean.TRUE.equals(aiResult.getHasSeal())
                        || Boolean.TRUE.equals(aiResult.getHasSignature()))
                .overallConfidence(scaleDecimal(evaluation.getSanitizedConfidence(), 2))
                .trustScore(scaleDecimal(evaluation.getTrustScore(), 2))
                .trustScoreBreakdown(serializeQuietly(evaluation.getBreakdown()))
                .identityVerificationStatus(evaluation.getIdentityStatus())
                .issuerVerificationStatus(evaluation.getIssuerStatus())
                .documentAuthenticityStatus(evaluation.getAuthenticityStatus())
                .verificationSource(evaluation.getVerificationSource())
                .verificationReference(evaluation.getVerificationReference())
                .routingDecision(evaluation.getFinalRoutingDecision())
                .routingReason(evaluation.getFinalReason())
                .status(evaluation.getFinalStatus())
                .humanReviewed(false)
                .spAwarded(spAwarded)
                .workerNotes(notes)
                .retryCount(0)
                .build();

        SkillVerificationRequest saved = verificationRequestRepository.save(entity);

        if (saved.getStatus() == SkillVerificationStatus.APPROVED) {
            applyApprovedSkills(worker, approvedSkills, resolvedJobType, saved.getId(), evaluation.getParsedExpiryDate());
        }

        recordAudit(
                worker.getUser() != null ? worker.getUser().getUserId() : null,
                "SKILL_VERIFICATION_SUBMITTED",
                saved.getId(),
                null,
                Map.of(
                        "status", saved.getStatus().name(),
                        "routingDecision", saved.getRoutingDecision().name(),
                        "trustScore", evaluation.getTrustScore(),
                        "spAwarded", spAwarded
                )
        );

        log.info("Skill verification evaluated [requestId={}, workerId={}, status={}, decision={}, trustScore={}]",
                saved.getId(), workerId, saved.getStatus(), saved.getRoutingDecision(), evaluation.getTrustScore());

        return mapToResponseDto(saved, aiResult.getExtractedSkills(), evaluation.getBreakdown(), worker);
    }

    @Transactional
    protected SkillVerificationResponseDto persistFailedVerification(
            UUID workerId,
            Skill requestedSkill,
            FileSecurityValidator.ValidatedDocument validatedDoc,
            String storedDocumentUrl,
            AiProviderException ex,
            String notes
    ) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + workerId));

        String jobType = (requestedSkill != null && requestedSkill.getName() != null)
                ? requestedSkill.getName()
                : DEFAULT_UNSPECIFIED_JOB_TYPE;

        SkillVerificationRequest entity = SkillVerificationRequest.builder()
                .worker(worker)
                .skill(requestedSkill)
                .jobType(jobType)
                .documentUrl(storedDocumentUrl)
                .documentName(validatedDoc.getSanitizedOriginalFilename())
                .documentType(validatedDoc.getDetectedMimeType())
                .documentHash(validatedDoc.getSha256Hash())
                .promptVersion(SkillVerificationPromptBuilder.PROMPT_VERSION)
                .hasSealOrSignature(false)
                .overallConfidence(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .trustScore(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .identityVerificationStatus(IdentityVerificationStatus.UNVERIFIED)
                .issuerVerificationStatus(IssuerVerificationStatus.UNKNOWN_ISSUER)
                .documentAuthenticityStatus(DocumentAuthenticityStatus.UNVERIFIED)
                .verificationSource("AI_PROVIDER_FAILED")
                .routingDecision(RoutingDecision.REJECT)
                .routingReason(ex.getUserSafeReason())
                .status(SkillVerificationStatus.VERIFICATION_FAILED)
                .humanReviewed(false)
                .failureReason(ex.getErrorCode() + ": " + ex.getUserSafeReason())
                .retryCount(0)
                .spAwarded(0)
                .workerNotes(notes)
                .build();

        SkillVerificationRequest saved = verificationRequestRepository.save(entity);

        recordAudit(
                worker.getUser() != null ? worker.getUser().getUserId() : null,
                "SKILL_VERIFICATION_AI_FAILED",
                saved.getId(),
                null,
                Map.of("errorCode", ex.getErrorCode(), "retryable", ex.isRetryable())
        );

        return mapToResponseDto(saved, Collections.emptyList(), null, worker);
    }

    @Transactional
    protected SkillVerificationResponseDto updateResubmittedRequest(
            UUID verificationId,
            FileSecurityValidator.ValidatedDocument validatedDoc,
            String storedDocumentUrl,
            SkillVerificationAiResultDto aiResult,
            TrustEvaluationResult evaluation,
            String notes,
            List<Skill> activeSkills
    ) {
        SkillVerificationRequest req = verificationRequestRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu: " + verificationId));
        String oldStatus = req.getStatus().name();

        Worker worker = req.getWorker();
        List<Skill> approvedSkills = evaluation.getFinalStatus() == SkillVerificationStatus.APPROVED
                ? resolveSkillsForApproval(evaluation.getMatchedApprovedSkills(), req.getSkill())
                : Collections.emptyList();

        int spAwarded = (evaluation.getFinalStatus() == SkillVerificationStatus.APPROVED
                && (req.getSpAwarded() == null || req.getSpAwarded() == 0))
                ? Math.max(VerificationTier.AI_DOCUMENT.getSpVerify(),
                approvedSkills.size() * VerificationTier.AI_DOCUMENT.getSpVerify())
                : 0;

        req.setDocumentUrl(storedDocumentUrl);
        req.setDocumentName(validatedDoc.getSanitizedOriginalFilename());
        req.setDocumentType(validatedDoc.getDetectedMimeType());
        req.setDocumentHash(validatedDoc.getSha256Hash());
        if (notes != null) {
            req.setWorkerNotes(notes);
        }
        applyAiEvaluationToEntity(req, aiResult, evaluation, activeSkills, approvedSkills, spAwarded);
        req.setFailureReason(null);
        req.setHumanReviewed(false);

        SkillVerificationRequest saved = verificationRequestRepository.save(req);

        if (saved.getStatus() == SkillVerificationStatus.APPROVED && spAwarded > 0) {
            applyApprovedSkills(worker, approvedSkills, saved.getJobType(), saved.getId(), evaluation.getParsedExpiryDate());
        }

        recordAudit(
                worker.getUser() != null ? worker.getUser().getUserId() : null,
                "SKILL_VERIFICATION_RESUBMITTED",
                saved.getId(),
                Map.of("status", oldStatus),
                Map.of("status", saved.getStatus().name(), "trustScore", evaluation.getTrustScore())
        );

        return mapToResponseDto(saved, aiResult.getExtractedSkills(), evaluation.getBreakdown(), worker);
    }

    @Transactional
    protected SkillVerificationResponseDto applyRetrySuccess(
            UUID verificationId,
            UUID adminUserId,
            SkillVerificationAiResultDto aiResult,
            TrustEvaluationResult evaluation,
            List<Skill> activeSkills
    ) {
        SkillVerificationRequest req = verificationRequestRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu: " + verificationId));

        if (req.getStatus() != SkillVerificationStatus.VERIFICATION_FAILED) {
            throw new BadRequestException("Hồ sơ đã thay đổi trạng thái trước khi hoàn tất thử lại.");
        }

        Worker worker = req.getWorker();
        List<Skill> approvedSkills = evaluation.getFinalStatus() == SkillVerificationStatus.APPROVED
                ? resolveSkillsForApproval(evaluation.getMatchedApprovedSkills(), req.getSkill())
                : Collections.emptyList();

        int spAwarded = (evaluation.getFinalStatus() == SkillVerificationStatus.APPROVED
                && (req.getSpAwarded() == null || req.getSpAwarded() == 0))
                ? Math.max(VerificationTier.AI_DOCUMENT.getSpVerify(),
                approvedSkills.size() * VerificationTier.AI_DOCUMENT.getSpVerify())
                : 0;

        req.setRetryCount((req.getRetryCount() != null ? req.getRetryCount() : 0) + 1);
        req.setFailureReason(null);
        applyAiEvaluationToEntity(req, aiResult, evaluation, activeSkills, approvedSkills, spAwarded);

        SkillVerificationRequest saved = verificationRequestRepository.save(req);

        if (saved.getStatus() == SkillVerificationStatus.APPROVED && spAwarded > 0) {
            applyApprovedSkills(worker, approvedSkills, saved.getJobType(), saved.getId(), evaluation.getParsedExpiryDate());
        }

        recordAudit(
                adminUserId,
                "SKILL_VERIFICATION_RETRIED",
                saved.getId(),
                Map.of("status", SkillVerificationStatus.VERIFICATION_FAILED.name()),
                Map.of("status", saved.getStatus().name(), "retryCount", saved.getRetryCount())
        );

        return mapToResponseDto(saved, aiResult.getExtractedSkills(), evaluation.getBreakdown(), worker);
    }

    @Transactional
    protected SkillVerificationResponseDto updateFailedRequest(
            UUID verificationId,
            FileSecurityValidator.ValidatedDocument validatedDoc,
            String storedDocumentUrl,
            AiProviderException ex,
            String notes,
            boolean incrementRetry
    ) {
        SkillVerificationRequest req = verificationRequestRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu: " + verificationId));

        if (validatedDoc != null) {
            req.setDocumentUrl(storedDocumentUrl);
            req.setDocumentName(validatedDoc.getSanitizedOriginalFilename());
            req.setDocumentType(validatedDoc.getDetectedMimeType());
            req.setDocumentHash(validatedDoc.getSha256Hash());
        }
        if (notes != null) {
            req.setWorkerNotes(notes);
        }
        if (incrementRetry) {
            req.setRetryCount((req.getRetryCount() != null ? req.getRetryCount() : 0) + 1);
        }
        req.setStatus(SkillVerificationStatus.VERIFICATION_FAILED);
        req.setRoutingDecision(RoutingDecision.REJECT);
        req.setRoutingReason(ex.getUserSafeReason());
        req.setFailureReason(ex.getErrorCode() + ": " + ex.getUserSafeReason());
        req.setSpAwarded(0);

        SkillVerificationRequest saved = verificationRequestRepository.save(req);
        return mapToResponseDto(saved, Collections.emptyList(), null, saved.getWorker());
    }

    private void applyAiEvaluationToEntity(
            SkillVerificationRequest req,
            SkillVerificationAiResultDto aiResult,
            TrustEvaluationResult evaluation,
            List<Skill> activeSkills,
            List<Skill> approvedSkills,
            int spAwarded
    ) {
        String resolvedJobType = resolveJobType(aiResult, req.getSkill(), approvedSkills);
        Skill resolvedSkill = resolveLinkedSkill(req.getSkill(), approvedSkills, aiResult, activeSkills);

        req.setSkill(resolvedSkill);
        req.setJobType(resolvedJobType);
        req.setRecipientName(aiResult.getRecipientName());
        req.setIdentityNumber(aiResult.getIdentityNumber());
        req.setInstitution(aiResult.getInstitution());
        req.setCertificateNumber(aiResult.getCertificateNumber());
        req.setIssueDate(aiResult.getIssueDate());
        req.setExpiryDate(aiResult.getExpiryDate());
        req.setModelName(aiResult.getModelName());
        req.setPromptVersion(aiResult.getPromptVersion() != null
                ? aiResult.getPromptVersion()
                : SkillVerificationPromptBuilder.PROMPT_VERSION);
        req.setAiRawResponse(sanitizeJsonOrNull(aiResult.getRawAiJson()));
        req.setExtractedSkills(serializeQuietly(aiResult.getExtractedSkills()));
        req.setHasSealOrSignature(Boolean.TRUE.equals(aiResult.getHasSealOrSignature())
                || Boolean.TRUE.equals(aiResult.getHasSeal())
                || Boolean.TRUE.equals(aiResult.getHasSignature()));
        req.setOverallConfidence(scaleDecimal(evaluation.getSanitizedConfidence(), 2));
        req.setTrustScore(scaleDecimal(evaluation.getTrustScore(), 2));
        req.setTrustScoreBreakdown(serializeQuietly(evaluation.getBreakdown()));
        req.setIdentityVerificationStatus(evaluation.getIdentityStatus());
        req.setIssuerVerificationStatus(evaluation.getIssuerStatus());
        req.setDocumentAuthenticityStatus(evaluation.getAuthenticityStatus());
        req.setVerificationSource(evaluation.getVerificationSource());
        req.setVerificationReference(evaluation.getVerificationReference());
        req.setRoutingDecision(evaluation.getFinalRoutingDecision());
        req.setRoutingReason(evaluation.getFinalReason());
        req.setStatus(evaluation.getFinalStatus());
        req.setSpAwarded(spAwarded);
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

    /**
     * Backward-compatible overload cho Admin review (boolean approved).
     */
    @Transactional
    public SkillVerificationResponseDto adminReview(UUID verificationId, UUID adminUserId, boolean approved, String reviewNotes) {
        AdminReviewDecision decision = approved ? AdminReviewDecision.APPROVE : AdminReviewDecision.REJECT;
        return adminReview(verificationId, adminUserId, decision, reviewNotes);
    }

    /**
     * Admin / Customer Care manual review (UC-47 V2): hỗ trợ APPROVE, REJECT, NEEDS_MORE_INFO kèm AuditLog.
     */
    @Transactional
    public SkillVerificationResponseDto adminReview(
            UUID verificationId,
            UUID adminUserId,
            AdminReviewDecision decision,
            String reviewNotes
    ) {
        if (decision == null) {
            throw new BadRequestException("Vui lòng chọn quyết định thẩm định (APPROVE, REJECT hoặc NEEDS_MORE_INFO).");
        }

        SkillVerificationRequest req = verificationRequestRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thẩm định với ID: " + verificationId));

        if (req.getStatus() != SkillVerificationStatus.PENDING_REVIEW
                && req.getStatus() != SkillVerificationStatus.VERIFICATION_FAILED) {
            throw new BadRequestException("Yêu cầu này đã được xử lý trước đó với trạng thái: " + req.getStatus());
        }

        if ((decision == AdminReviewDecision.REJECT || decision == AdminReviewDecision.NEEDS_MORE_INFO)
                && (reviewNotes == null || reviewNotes.isBlank())) {
            throw new BadRequestException("Vui lòng nhập ghi chú lý do khi từ chối hoặc yêu cầu bổ sung tài liệu.");
        }

        String oldStatus = req.getStatus().name();
        Worker worker = req.getWorker();

        if (decision == AdminReviewDecision.APPROVE) {
            // Chống cấp điểm trùng lặp nếu đã cấp trước đó
            if (req.getSpAwarded() != null && req.getSpAwarded() > 0) {
                throw new BadRequestException("Yêu cầu này đã được cấp điểm kỹ năng trước đó.");
            }
            req.setStatus(SkillVerificationStatus.APPROVED);
            List<Skill> skillsToApprove = resolveSkillsFromStoredRequest(req);
            int spAwarded = Math.max(
                    VerificationTier.AI_DOCUMENT.getSpVerify(),
                    skillsToApprove.size() * VerificationTier.AI_DOCUMENT.getSpVerify()
            );
            req.setSpAwarded(spAwarded);
            applyApprovedSkills(worker, skillsToApprove, req.getJobType(), req.getId(), null);
        } else if (decision == AdminReviewDecision.NEEDS_MORE_INFO) {
            req.setStatus(SkillVerificationStatus.NEEDS_MORE_INFO);
            req.setSpAwarded(0);
        } else {
            req.setStatus(SkillVerificationStatus.REJECTED);
            req.setSpAwarded(0);
        }

        req.setHumanReviewed(true);
        req.setReviewedBy(adminUserId);
        req.setReviewedAt(Instant.now());
        req.setReviewNotes(reviewNotes);

        SkillVerificationRequest saved = verificationRequestRepository.save(req);

        recordAudit(
                adminUserId,
                "SKILL_VERIFICATION_ADMIN_" + decision.name(),
                saved.getId(),
                Map.of("status", oldStatus),
                Map.of(
                        "status", saved.getStatus().name(),
                        "spAwarded", saved.getSpAwarded() != null ? saved.getSpAwarded() : 0,
                        "reviewNotes", reviewNotes != null ? reviewNotes : ""
                )
        );

        return mapToResponseDto(saved, null, null, worker);
    }

    @Transactional(readOnly = true)
    public List<SkillVerificationResponseDto> getPendingVerificationQueue() {
        List<SkillVerificationRequest> pending = verificationRequestRepository
                .findByStatusOrderByCreatedAtAsc(SkillVerificationStatus.PENDING_REVIEW);

        return pending.stream()
                .map(req -> mapToResponseDto(req, null, null, req.getWorker()))
                .collect(Collectors.toList());
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
        int spToDeduct = req.getSpAwarded() != null ? req.getSpAwarded() : VerificationTier.AI_DOCUMENT.getSpVerify();
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
                    remaining = Math.max(remaining, VerificationTier.PLATFORM_PROVEN.getSpVerify());
                } else if (currentWs.getVerificationTier() == VerificationTier.SELF_DECLARED) {
                    remaining = 0;
                }
                newVerify = Math.max(0, remaining);
            } else {
                int currentVerify = sp.getSpVerify() != null ? sp.getSpVerify() : 0;
                int deductForSkill = Math.min(spToDeduct, VerificationTier.AI_DOCUMENT.getSpVerify());
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
        SkillTrustScoringEngine.normalizeName(workerName); // reuse strict logic
        if (workerName == null || recipientName == null) {
            return false;
        }
        String normWorker = SkillTrustScoringEngine.normalizeName(workerName);
        String normRecipient = SkillTrustScoringEngine.normalizeName(recipientName);
        if (normWorker.equals(normRecipient)) {
            return true;
        }
        String[] wTokens = normWorker.split("\\s+");
        String[] rTokens = normRecipient.split("\\s+");
        if (wTokens.length >= 2 && rTokens.length >= 2) {
            boolean firstMatches = wTokens[0].equals(rTokens[0]);
            boolean lastMatches = wTokens[wTokens.length - 1].equals(rTokens[rTokens.length - 1]);
            if (firstMatches && lastMatches) {
                // Nếu cả hai bên đều có tên đệm mà khác nhau -> không khớp
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

    public static String removeDiacritics(String str) {
        return SkillTrustScoringEngine.normalizeName(str);
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

            // Không hạ cấp từ PLATFORM_PROVEN xuống AI_DOCUMENT
            if (ws.getVerificationTier() != VerificationTier.PLATFORM_PROVEN) {
                ws.setVerificationTier(VerificationTier.AI_DOCUMENT);
                ws.setSpVerifyAwarded(Math.max(
                        ws.getSpVerifyAwarded() != null ? ws.getSpVerifyAwarded() : 0,
                        VerificationTier.AI_DOCUMENT.getSpVerify()
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
                        : VerificationTier.AI_DOCUMENT.getSpVerify();

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
                        VerificationTier.AI_DOCUMENT.getSpVerify()
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

    private List<Skill> resolveSkillsForApproval(List<Skill> matchedSkills, Skill requestedSkill) {
        List<Skill> result = new ArrayList<>();
        if (matchedSkills != null) {
            result.addAll(matchedSkills);
        }
        if (result.isEmpty() && requestedSkill != null) {
            result.add(requestedSkill);
        }
        return result;
    }

    private List<Skill> resolveSkillsFromStoredRequest(SkillVerificationRequest req) {
        List<Skill> skills = new ArrayList<>();
        if (req.getSkill() != null) {
            skills.add(req.getSkill());
        } else if (req.getJobType() != null && !req.getJobType().isBlank()
                && !DEFAULT_UNSPECIFIED_JOB_TYPE.equals(req.getJobType())) {
            skillRepository.findByNameIgnoreCase(req.getJobType()).ifPresent(skills::add);
        }
        return skills;
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

    private Skill resolveLinkedSkill(
            Skill requestedSkill,
            List<Skill> approvedSkills,
            SkillVerificationAiResultDto aiResult,
            List<Skill> activeSkills
    ) {
        if (requestedSkill != null) {
            return requestedSkill;
        }
        if (approvedSkills != null && !approvedSkills.isEmpty()) {
            return approvedSkills.get(0);
        }
        if (aiResult != null && aiResult.getExtractedSkills() != null && activeSkills != null) {
            Map<String, Skill> byName = activeSkills.stream()
                    .filter(s -> s.getName() != null)
                    .collect(Collectors.toMap(s -> s.getName().toLowerCase(), s -> s, (a, b) -> a));
            for (ExtractedSkillDto ext : aiResult.getExtractedSkills()) {
                if (ext.getTaxonomyRole() != null) {
                    Skill matched = byName.get(ext.getTaxonomyRole().trim().toLowerCase());
                    if (matched != null) {
                        return matched;
                    }
                }
            }
        }
        return null;
    }

    private String resolveJobType(SkillVerificationAiResultDto aiResult, Skill requestedSkill, List<Skill> approvedSkills) {
        if (requestedSkill != null && requestedSkill.getName() != null) {
            return requestedSkill.getName();
        }
        if (approvedSkills != null && !approvedSkills.isEmpty()) {
            return approvedSkills.get(0).getName();
        }
        if (aiResult != null && aiResult.getExtractedSkills() != null && !aiResult.getExtractedSkills().isEmpty()) {
            String role = aiResult.getExtractedSkills().get(0).getTaxonomyRole();
            if (role != null && !role.isBlank() && !role.equalsIgnoreCase("N/A")) {
                return role;
            }
        }
        return DEFAULT_UNSPECIFIED_JOB_TYPE;
    }

    private String resolveWorkerFullName(Worker worker) {
        if (worker.getFullName() != null && !worker.getFullName().isBlank()) {
            return worker.getFullName();
        }
        return (worker.getUser() != null && worker.getUser().getFullName() != null)
                ? worker.getUser().getFullName()
                : "";
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

    private BigDecimal scaleDecimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
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

    private String sanitizeJsonOrNull(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            objectMapper.readTree(rawJson);
            return rawJson;
        } catch (Exception e) {
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
        String normalizedInput = SkillTrustScoringEngine.normalizeName(rawName);
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
            String normSkill = SkillTrustScoringEngine.normalizeName(skill.getName());
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
                    .filter(s -> s.getName() != null && SkillTrustScoringEngine.normalizeName(s.getName()).contains(hint))
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
