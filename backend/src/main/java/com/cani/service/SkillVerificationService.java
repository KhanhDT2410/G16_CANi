package com.cani.service;

import com.cani.client.gemini.GeminiVisionClient;
import com.cani.dto.skill.*;
import com.cani.entity.Skill;
import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.User;
import com.cani.entity.Worker;
import com.cani.entity.WorkerSkill;
import com.cani.entity.WorkerSkillPoint;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.entity.enums.UserRole;
import com.cani.entity.enums.VerificationTier;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.SkillRepository;
import com.cani.repository.SkillVerificationRequestRepository;
import com.cani.repository.WorkerRepository;
import com.cani.repository.WorkerSkillPointRepository;
import com.cani.repository.WorkerSkillRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillVerificationService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf",
            "text/plain" // allowed for unit tests / sample mocks
    );

    private final WorkerRepository workerRepository;
    private final SkillRepository skillRepository;
    private final WorkerSkillRepository workerSkillRepository;
    private final WorkerSkillPointRepository workerSkillPointRepository;
    private final SkillVerificationRequestRepository verificationRequestRepository;
    private final GeminiVisionClient geminiVisionClient;
    private final ObjectMapper objectMapper;

    /**
     * Submit certificate/credential for AI Verification (UC-43 & UC-46).
     * NOTE: This outer method is intentionally NOT @Transactional to prevent holding
     * a database connection pool slot while waiting 5-20s for the external Gemini API call.
     */
    public SkillVerificationResponseDto submitVerification(UUID workerId, MultipartFile file, UUID requestedSkillId, String notes) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin Worker với ID: " + workerId));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng tải lên tệp ảnh chứng chỉ hoặc bằng cấp hợp lệ (JPG, PNG, PDF).");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Dung lượng tệp tải lên vượt quá giới hạn 5MB.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Định dạng tệp không được hỗ trợ. Vui lòng chỉ tải lên JPG, PNG hoặc PDF.");
        }

        Skill requestedSkill = null;
        if (requestedSkillId != null) {
            requestedSkill = skillRepository.findById(requestedSkillId).orElse(null);
        }

        List<Skill> activeSkills = skillRepository.findByIsActiveTrue();
        List<String> taxonomyNames = activeSkills.stream().map(Skill::getName).collect(Collectors.toList());

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new BadRequestException("Không thể đọc nội dung tệp tải lên: " + e.getMessage());
        }

        // 1. Call Gemini AI OUTSIDE of database transaction
        GeminiVerificationResultDto aiResult = geminiVisionClient.verifyDocument(
                fileBytes, contentType, taxonomyNames
        );

        // 2. Persist verification result and update SP/tiers inside short @Transactional method
        return persistVerificationResult(workerId, requestedSkill, file, aiResult, notes, activeSkills);
    }

    /**
     * Atomic persistence of AI verification outcome, awarded SP, and skill tier upgrades.
     */
    @Transactional
    public SkillVerificationResponseDto persistVerificationResult(
            UUID workerId,
            Skill requestedSkill,
            MultipartFile file,
            GeminiVerificationResultDto aiResult,
            String notes,
            List<Skill> activeSkills
    ) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + workerId));

        String resolvedJobType = resolveJobType(aiResult, requestedSkill);

        SkillVerificationStatus status;
        int spAwarded = 0;

        // Collect skills to approve
        List<Skill> approvedSkills = new ArrayList<>();
        if (aiResult.getRoutingDecision() == RoutingDecision.AUTO_APPROVE) {
            status = SkillVerificationStatus.APPROVED;

            // Match extracted skills with database skills
            Map<String, Skill> skillNameMap = activeSkills.stream()
                    .collect(Collectors.toMap(s -> s.getName().toLowerCase(), s -> s, (s1, s2) -> s1));

            if (aiResult.getExtractedSkills() != null && !aiResult.getExtractedSkills().isEmpty()) {
                for (ExtractedSkillDto ext : aiResult.getExtractedSkills()) {
                    if (ext.getTaxonomyRole() != null) {
                        Skill matched = skillNameMap.get(ext.getTaxonomyRole().toLowerCase());
                        if (matched != null && !approvedSkills.contains(matched)) {
                            approvedSkills.add(matched);
                        }
                    }
                }
            }

            if (approvedSkills.isEmpty() && requestedSkill != null) {
                approvedSkills.add(requestedSkill);
            }

            // BR-65: +25 SP_verify per approved skill
            spAwarded = Math.max(25, approvedSkills.size() * VerificationTier.AI_DOCUMENT.getSpVerify());
        } else if (aiResult.getRoutingDecision() == RoutingDecision.ADMIN_REVIEW) {
            status = SkillVerificationStatus.PENDING_REVIEW;
        } else {
            status = SkillVerificationStatus.REJECTED;
        }

        String extractedSkillsJson = null;
        try {
            extractedSkillsJson = objectMapper.writeValueAsString(aiResult.getExtractedSkills());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize extracted skills to JSON: {}", e.getMessage());
        }

        SkillVerificationRequest entity = SkillVerificationRequest.builder()
                .worker(worker)
                .skill(requestedSkill != null ? requestedSkill : (!approvedSkills.isEmpty() ? approvedSkills.get(0) : null))
                .jobType(resolvedJobType)
                .documentName(file != null ? file.getOriginalFilename() : "document")
                .documentType(file != null ? file.getContentType() : "application/octet-stream")
                .recipientName(aiResult.getRecipientName())
                .identityNumber(aiResult.getIdentityNumber())
                .institution(aiResult.getInstitution())
                .issueDate(aiResult.getIssueDate())
                .extractedSkills(extractedSkillsJson)
                .hasSealOrSignature(aiResult.getHasSealOrSignature())
                .overallConfidence(BigDecimal.valueOf(aiResult.getOverallConfidence()))
                .routingDecision(aiResult.getRoutingDecision())
                .routingReason(aiResult.getRoutingReason())
                .status(status)
                .spAwarded(spAwarded)
                .workerNotes(notes)
                .build();

        SkillVerificationRequest saved = verificationRequestRepository.save(entity);

        if (status == SkillVerificationStatus.APPROVED) {
            applyApprovedSkills(worker, approvedSkills, resolvedJobType, saved.getId());
        }

        return mapToResponseDto(saved, aiResult.getExtractedSkills(), worker);
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
        Set<UUID> allSkillIds = new HashSet<>();
        if (worker.getSkills() != null) {
            allSkillIds.addAll(Arrays.asList(worker.getSkills()));
        }

        for (DeclareSkillRequestDto.SkillItem item : request.getSkills()) {
            Skill skill = skillRepository.findById(item.getSkillId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỹ năng: " + item.getSkillId()));

            WorkerSkill ws = workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(workerId, skill.getSkillId())
                    .orElseGet(() -> WorkerSkill.builder()
                            .worker(worker)
                            .skill(skill)
                            .proficiency(item.getProficiency())
                            .verificationTier(VerificationTier.SELF_DECLARED)
                            .spVerifyAwarded(0)
                            .build()
                    );

            // Update proficiency level (Beginner/Intermediate/Advanced/Expert)
            ws.setProficiency(item.getProficiency());

            // If it's a new or self-declared record, keep SP_verify = 0. Do not downgrade AI_DOCUMENT.
            if (ws.getVerificationTier() == null || ws.getVerificationTier() == VerificationTier.SELF_DECLARED) {
                ws.setVerificationTier(VerificationTier.SELF_DECLARED);
                ws.setSpVerifyAwarded(0);
            }

            WorkerSkill saved = workerSkillRepository.save(ws);
            resultList.add(saved);
            allSkillIds.add(skill.getSkillId());

            // Ensure baseline WorkerSkillPoint exists for this job_type
            ensureWorkerSkillPoint(worker, skill.getName());
        }

        // Sync legacy skills UUID[] array
        worker.setSkills(allSkillIds.toArray(new UUID[0]));
        workerRepository.save(worker);

        return resultList.stream().map(this::mapToWorkerSkillDto).collect(Collectors.toList());
    }

    /**
     * Get all declared and verified skills of a worker (BR-16, BR-17)
     */
    @Transactional(readOnly = true)
    public List<WorkerSkillResponseDto> getWorkerSkills(UUID workerId) {
        List<WorkerSkill> list = workerSkillRepository.findByWorker_WorkerId(workerId);
        return list.stream().map(this::mapToWorkerSkillDto).collect(Collectors.toList());
    }

    /**
     * Get standard tier configuration based on BR-64
     */
    public List<TierConfigDto> getTierConfigs() {
        return Arrays.stream(SkillTier.values())
                .map(t -> TierConfigDto.builder()
                        .tier(t)
                        .minSp(t.getMinSp())
                        .nextTierMinSp(t.next() != null ? t.next().getMinSp() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get verification history for a worker
     */
    @Transactional(readOnly = true)
    public List<SkillVerificationResponseDto> getWorkerVerificationHistory(UUID workerId) {
        List<SkillVerificationRequest> list = verificationRequestRepository.findByWorker_WorkerIdOrderByCreatedAtDesc(workerId);
        return list.stream().map(req -> mapToResponseDto(req, null, req.getWorker())).collect(Collectors.toList());
    }

    /**
     * Get Skill Points breakdown by jobType for a worker
     */
    @Transactional(readOnly = true)
    public List<WorkerSkillPointDto> getWorkerSkillPoints(UUID workerId) {
        List<WorkerSkillPoint> points = workerSkillPointRepository.findByWorker_WorkerId(workerId);
        return points.stream().map(p -> WorkerSkillPointDto.builder()
                .id(p.getSpId())
                .jobType(p.getJobType())
                .spVerify(p.getSpVerify())
                .spExperience(p.getSpExperience())
                .spRating(p.getSpRating())
                .totalSp(p.getTotalSp())
                .tier(p.getTier())
                .totalShiftsCompleted(p.getTotalShiftsCompleted())
                .avgRating(p.getAvgRating())
                .updatedAt(p.getUpdatedAt())
                .build()
        ).collect(Collectors.toList());
    }

    /**
     * Admin manual review for Medium-confidence submissions (UC-47)
     */
    @Transactional
    public SkillVerificationResponseDto adminReview(UUID verificationId, UUID adminUserId, boolean approved, String reviewNotes) {
        SkillVerificationRequest req = verificationRequestRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu thẩm định với ID: " + verificationId));

        if (req.getStatus() != SkillVerificationStatus.PENDING_REVIEW) {
            throw new BadRequestException("Yêu cầu này đã được xử lý trước đó với trạng thái: " + req.getStatus());
        }

        Worker worker = req.getWorker();
        int spAwarded = 0;

        if (approved) {
            req.setStatus(SkillVerificationStatus.APPROVED);
            spAwarded = VerificationTier.AI_DOCUMENT.getSpVerify(); // +25 SP
            req.setSpAwarded(spAwarded);

            List<Skill> skillsToApprove = new ArrayList<>();
            if (req.getSkill() != null) {
                skillsToApprove.add(req.getSkill());
            }
            applyApprovedSkills(worker, skillsToApprove, req.getJobType(), req.getId());
        } else {
            req.setStatus(SkillVerificationStatus.REJECTED);
            req.setSpAwarded(0);
        }

        req.setReviewedBy(adminUserId);
        req.setReviewNotes(reviewNotes);

        SkillVerificationRequest saved = verificationRequestRepository.save(req);
        return mapToResponseDto(saved, null, worker);
    }

    private void applyApprovedSkills(Worker worker, List<Skill> skills, String jobType, UUID requestId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(730, ChronoUnit.DAYS); // BR-17: 24 months validity

        Set<UUID> skillIds = new HashSet<>();
        if (worker.getSkills() != null) {
            skillIds.addAll(Arrays.asList(worker.getSkills()));
        }

        for (Skill s : skills) {
            skillIds.add(s.getSkillId());
            WorkerSkill ws = workerSkillRepository.findByWorker_WorkerIdAndSkill_SkillId(worker.getWorkerId(), s.getSkillId())
                    .orElseGet(() -> WorkerSkill.builder()
                            .worker(worker)
                            .skill(s)
                            .build()
                    );

            ws.setVerificationTier(VerificationTier.AI_DOCUMENT);
            ws.setSpVerifyAwarded(VerificationTier.AI_DOCUMENT.getSpVerify()); // +25 per skill (BR-65)
            ws.setVerifiedAt(now);
            ws.setExpiresAt(expiresAt);
            ws.setEvidenceRequestId(requestId);
            workerSkillRepository.save(ws);
        }

        // Update legacy skills UUID[] array
        worker.setSkills(skillIds.toArray(new UUID[0]));

        // Update WorkerSkillPoint for effective job type
        String effectiveJobType = (jobType != null && !jobType.isBlank())
                ? jobType
                : (!skills.isEmpty() ? skills.get(0).getName() : "Phục vụ bàn");

        WorkerSkillPoint wsp = workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), effectiveJobType)
                .orElseGet(() -> WorkerSkillPoint.builder()
                        .worker(worker)
                        .jobType(effectiveJobType)
                        .spVerify(0)
                        .spExperience(0)
                        .spRating(0)
                        .totalSp(0)
                        .tier(SkillTier.STARTER)
                        .build()
                );

        // Recalculate total sp_verify from worker_skills for this job type
        int totalSpVerifyForJob = workerSkillRepository.findByWorker_WorkerId(worker.getWorkerId()).stream()
                .filter(ws -> ws.getSkill() != null && ws.getSkill().getName().equalsIgnoreCase(effectiveJobType))
                .mapToInt(WorkerSkill::getSpVerifyAwarded)
                .sum();

        // If skill name doesn't match directly, credit the awarded SP
        if (totalSpVerifyForJob == 0) {
            totalSpVerifyForJob = wsp.getSpVerify() + (skills.size() * VerificationTier.AI_DOCUMENT.getSpVerify());
        }

        wsp.setSpVerify(totalSpVerifyForJob);
        wsp.setTotalSp(wsp.getSpVerify() + wsp.getSpExperience() + wsp.getSpRating());

        // BR-64: Single Source of Truth for Tier calculation
        SkillTier calculatedTier = SkillTier.fromTotalSp(wsp.getTotalSp());
        wsp.setTier(calculatedTier);
        workerSkillPointRepository.save(wsp);

        // Update Worker overall skill tier if higher
        if (calculatedTier.ordinal() > worker.getSkillTier().ordinal()) {
            worker.setSkillTier(calculatedTier);
        }
        workerRepository.save(worker);

        log.info("Worker {} approved for jobType '{}', total SP: {}, Tier: {}",
                worker.getWorkerId(), effectiveJobType, wsp.getTotalSp(), calculatedTier);
    }

    private void ensureWorkerSkillPoint(Worker worker, String jobType) {
        if (jobType == null || jobType.isBlank()) return;
        workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), jobType)
                .orElseGet(() -> workerSkillPointRepository.save(WorkerSkillPoint.builder()
                        .worker(worker)
                        .jobType(jobType)
                        .spVerify(0)
                        .spExperience(0)
                        .spRating(0)
                        .totalSp(0)
                        .tier(SkillTier.STARTER)
                        .build()
                ));
    }

    private String resolveJobType(GeminiVerificationResultDto aiResult, Skill requestedSkill) {
        if (requestedSkill != null && requestedSkill.getName() != null) {
            return requestedSkill.getName();
        }
        if (aiResult.getExtractedSkills() != null && !aiResult.getExtractedSkills().isEmpty()) {
            String role = aiResult.getExtractedSkills().get(0).getTaxonomyRole();
            if (role != null && !role.isBlank() && !role.equalsIgnoreCase("N/A")) {
                return role;
            }
        }
        return "Phục vụ bàn";
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

    private SkillVerificationResponseDto mapToResponseDto(SkillVerificationRequest req, List<ExtractedSkillDto> skillsOverride, Worker worker) {
        List<ExtractedSkillDto> skills = skillsOverride;
        if (skills == null && req.getExtractedSkills() != null) {
            try {
                ExtractedSkillDto[] arr = objectMapper.readValue(req.getExtractedSkills(), ExtractedSkillDto[].class);
                skills = Arrays.asList(arr);
            } catch (Exception e) {
                skills = Collections.emptyList();
            }
        }

        int totalSp = 0;
        if (worker != null && req.getJobType() != null) {
            totalSp = workerSkillPointRepository.findByWorker_WorkerIdAndJobType(worker.getWorkerId(), req.getJobType())
                    .map(WorkerSkillPoint::getTotalSp)
                    .orElse(0);
        }

        return SkillVerificationResponseDto.builder()
                .id(req.getId())
                .workerId(req.getWorker().getWorkerId())
                .skillId(req.getSkill() != null ? req.getSkill().getSkillId() : null)
                .skillName(req.getSkill() != null ? req.getSkill().getName() : req.getJobType())
                .jobType(req.getJobType())
                .recipientName(req.getRecipientName())
                .identityNumber(req.getIdentityNumber())
                .institution(req.getInstitution())
                .issueDate(req.getIssueDate())
                .extractedSkills(skills)
                .hasSealOrSignature(req.getHasSealOrSignature())
                .overallConfidence(req.getOverallConfidence() != null ? req.getOverallConfidence().doubleValue() : 0.0)
                .routingDecision(req.getRoutingDecision())
                .routingReason(req.getRoutingReason())
                .status(req.getStatus())
                .spAwarded(req.getSpAwarded())
                .currentTotalSp(totalSp)
                .currentTier(worker != null ? worker.getSkillTier() : SkillTier.STARTER)
                .createdAt(req.getCreatedAt())
                .build();
    }

    /**
     * Get active Skill Taxonomy of the platform (BR-16).
     */
    @Transactional(readOnly = true)
    public List<Skill> getTaxonomy() {
        return skillRepository.findByIsActiveTrue();
    }

    /**
     * Resolve Worker ID from authenticated user or explicit ID for Admin.
     */
    @Transactional(readOnly = true)
    public UUID resolveWorkerId(User user, UUID explicitWorkerId) {
        if (user != null) {
            if (explicitWorkerId != null && user.getRole() == UserRole.ADMIN) {
                return explicitWorkerId;
            }
            Worker worker = workerRepository.findByUser_UserId(user.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Người dùng chưa có hồ sơ Worker trên hệ thống"));
            return worker.getWorkerId();
        }
        if (explicitWorkerId != null) {
            return explicitWorkerId;
        }
        throw new BadRequestException("Vui lòng đăng nhập để thực hiện thao tác này.");
    }
}
