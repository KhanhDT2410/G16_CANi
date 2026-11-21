package com.cani.service.skill;

import com.cani.dto.skill.ExtractedSkillDto;
import com.cani.dto.skill.SkillVerificationAiResultDto;
import com.cani.entity.Skill;
import com.cani.entity.enums.DocumentAuthenticityStatus;
import com.cani.entity.enums.IdentityVerificationStatus;
import com.cani.entity.enums.IssuerVerificationStatus;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillTrustScoringEngine — Hard Gates, TrustScore & Consistency Validator Tests")
class SkillTrustScoringEngineTest {

    private SkillTrustScoringEngine engine;
    private Skill baristaSkill;
    private Skill securitySkill;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-22T00:00:00Z"), ZoneOffset.UTC);
        AiOutputConsistencyValidator validator = new AiOutputConsistencyValidator(fixedClock);
        TrustedIssuerRegistry registry = new TrustedIssuerRegistry();
        engine = new SkillTrustScoringEngine(validator, registry);

        baristaSkill = Skill.builder()
                .skillId(UUID.randomUUID())
                .name("Pha chế (Bartender/Barista)")
                .category("F&B")
                .isActive(true)
                .build();

        securitySkill = Skill.builder()
                .skillId(UUID.randomUUID())
                .name("Bảo vệ / An ninh")
                .category("Security")
                .isActive(true)
                .build();
    }

    private ExtractedSkillDto baristaExtracted(double conf) {
        return ExtractedSkillDto.builder()
                .skillName("Barista Professional")
                .taxonomyRole("Pha chế (Bartender/Barista)")
                .confidenceScore(conf)
                .evidenceSnippet("Hoàn thành khóa Pha chế Barista")
                .build();
    }

    @Test
    @DisplayName("Chứng chỉ hợp lệ từ tổ chức uy tín (Saigontourist) -> AUTO_APPROVE & TrustScore >= 85")
    void evaluate_ValidTrustedCertificate_AutoApproves() {
        SkillVerificationAiResultDto ai = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName("Phạm Duy Hưng")
                .institution("Trường Trung cấp Du lịch & Khách sạn Saigontourist")
                .certificateNumber("SGT-2025-001")
                .issueDate("15/08/2025")
                .hasSeal(true)
                .hasSignature(true)
                .hasSealOrSignature(true)
                .identityMatch("EXACT")
                .documentAuthenticity("LIKELY_GENUINE_FORMAT")
                .skillRelevance("MATCH")
                .overallConfidence(0.95)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .extractedSkills(List.of(baristaExtracted(0.95)))
                .build();

        TrustEvaluationResult result = engine.evaluate("Phạm Duy Hưng", baristaSkill, List.of(baristaSkill, securitySkill), ai);

        assertEquals(SkillVerificationStatus.APPROVED, result.getFinalStatus());
        assertEquals(RoutingDecision.AUTO_APPROVE, result.getFinalRoutingDecision());
        assertTrue(result.getTrustScore() >= 85.0);
        assertEquals(IdentityVerificationStatus.EXACT_MATCH, result.getIdentityStatus());
        assertEquals(IssuerVerificationStatus.KNOWN_ISSUER_UNVERIFIED_ONLINE, result.getIssuerStatus());
        assertTrue(result.getBreakdown().getHardGatesTriggered().isEmpty());
    }

    @Test
    @DisplayName("Chứng chỉ quốc tế SCA có URL xác minh chính thức + mã chứng chỉ -> VERIFIED_ONLINE & TrustScore cao")
    void evaluate_InternationalCertificateWithOfficialUrl_VerifiedOnline() {
        SkillVerificationAiResultDto ai = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName("Hung Pham Duy")
                .institution("Specialty Coffee Association")
                .certificateNumber("SCA-998877")
                .verificationUrl("https://sca.coffee/verify/SCA-998877")
                .issueDate("2025-10-01")
                .hasVerificationQr(true)
                .hasVerificationUrl(true)
                .hasSignature(true)
                .hasSealOrSignature(true)
                .identityMatch("EXACT")
                .documentAuthenticity("LIKELY_GENUINE_FORMAT")
                .skillRelevance("MATCH")
                .overallConfidence(0.92)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .extractedSkills(List.of(baristaExtracted(0.94)))
                .build();

        TrustEvaluationResult result = engine.evaluate("Phạm Duy Hưng", baristaSkill, List.of(baristaSkill), ai);

        assertEquals(IssuerVerificationStatus.VERIFIED_ONLINE, result.getIssuerStatus());
        assertEquals(100, result.getBreakdown().getIssuerVerificationScore());
        assertEquals(SkillVerificationStatus.APPROVED, result.getFinalStatus());
    }

    @Test
    @DisplayName("Chứng chỉ quốc tế chưa xác thực nguồn phát hành -> REJECT kèm hướng dẫn hành động (không bắt Admin duyệt tay)")
    void evaluate_InternationalWithoutQr_RejectsWithActionableGuidance() {
        SkillVerificationAiResultDto ai = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName("Phạm Duy Hưng")
                .institution("London Coffee Institute")
                .issueDate("2025-05-01")
                .hasSignature(true)
                .hasSealOrSignature(true)
                .hasVerificationQr(false)
                .overallConfidence(0.76)
                .routingDecision(RoutingDecision.REJECT)
                .extractedSkills(List.of(baristaExtracted(0.85)))
                .build();

        TrustEvaluationResult result = engine.evaluate("Phạm Duy Hưng", baristaSkill, List.of(baristaSkill), ai);

        assertEquals(SkillVerificationStatus.REJECTED, result.getFinalStatus());
        assertEquals(RoutingDecision.REJECT, result.getFinalRoutingDecision());
        assertEquals(IssuerVerificationStatus.UNVERIFIED, result.getIssuerStatus());
        assertTrue(result.getBreakdown().getHardGatesTriggered().contains("HG-07_UNVERIFIED_ISSUER_OR_MISSING_AUTHORITY"));
        assertTrue(result.getFinalReason().contains("chưa đạt tiêu chuẩn tự động xác thực"));
    }

    @Test
    @DisplayName("Hard Gate HG-03: Khác tên đệm (Nguyễn Văn An vs Nguyễn Thị An) -> MISMATCH & REJECT (sửa lỗ hổng V1)")
    void evaluate_DifferentMiddleName_RejectsWithIdentityMismatch() {
        SkillVerificationAiResultDto ai = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName("Nguyễn Thị An")
                .institution("Saigontourist")
                .issueDate("2025-01-01")
                .hasSeal(true)
                .hasSealOrSignature(true)
                .overallConfidence(0.97)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .extractedSkills(List.of(baristaExtracted(0.95)))
                .build();

        TrustEvaluationResult result = engine.evaluate("Nguyễn Văn An", baristaSkill, List.of(baristaSkill), ai);

        assertEquals(IdentityVerificationStatus.MISMATCH, result.getIdentityStatus());
        assertEquals(0, result.getBreakdown().getIdentityScore());
        assertEquals(SkillVerificationStatus.REJECTED, result.getFinalStatus());
        assertTrue(result.getBreakdown().getHardGatesTriggered().contains("HG-03_IDENTITY_MISMATCH"));
    }

    @Test
    @DisplayName("Hard Gate HG-06: Thiếu tên trên chứng chỉ (recipientName=null) -> REJECT yêu cầu chụp rõ tên (không đưa vào Admin)")
    void evaluate_NullRecipientName_NeverAutoApproves() {
        SkillVerificationAiResultDto ai = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName(null)
                .institution("Saigontourist")
                .issueDate("2025-01-01")
                .hasSeal(true)
                .hasSealOrSignature(true)
                .overallConfidence(0.92)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .extractedSkills(List.of(baristaExtracted(0.92)))
                .build();

        TrustEvaluationResult result = engine.evaluate("Phạm Duy Hưng", baristaSkill, List.of(baristaSkill), ai);

        assertEquals(IdentityVerificationStatus.UNVERIFIED, result.getIdentityStatus());
        assertEquals(SkillVerificationStatus.REJECTED, result.getFinalStatus());
        assertEquals(RoutingDecision.REJECT, result.getFinalRoutingDecision());
        assertNotEquals(SkillVerificationStatus.APPROVED, result.getFinalStatus());
    }

    @Test
    @DisplayName("Hard Gate HG-04: Chứng chỉ bị chỉnh sửa/giả mạo (TAMPERED) -> REJECT ngay cả khi các điểm khác cao")
    void evaluate_TamperedDocument_HardRejects() {
        SkillVerificationAiResultDto ai = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName("Phạm Duy Hưng")
                .institution("Saigontourist")
                .issueDate("2025-01-01")
                .hasSeal(true)
                .documentAuthenticity("TAMPERED")
                .riskFlags(List.of("SUSPECTED_TAMPERING"))
                .overallConfidence(0.95)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .extractedSkills(List.of(baristaExtracted(0.95)))
                .build();

        TrustEvaluationResult result = engine.evaluate("Phạm Duy Hưng", baristaSkill, List.of(baristaSkill), ai);

        assertEquals(DocumentAuthenticityStatus.TAMPERED, result.getAuthenticityStatus());
        assertEquals(SkillVerificationStatus.REJECTED, result.getFinalStatus());
        assertTrue(result.getBreakdown().getHardGatesTriggered().contains("HG-04_DOCUMENT_TAMPERED"));
    }

    @Test
    @DisplayName("Hard Gate HG-05: Chứng chỉ đã hết hạn -> REJECT")
    void evaluate_ExpiredCertificate_Rejects() {
        SkillVerificationAiResultDto ai = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName("Phạm Duy Hưng")
                .institution("Saigontourist")
                .issueDate("2020-01-01")
                .expiryDate("2022-01-01")
                .hasSeal(true)
                .hasSealOrSignature(true)
                .overallConfidence(0.95)
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .extractedSkills(List.of(baristaExtracted(0.95)))
                .build();

        TrustEvaluationResult result = engine.evaluate("Phạm Duy Hưng", baristaSkill, List.of(baristaSkill), ai);

        assertEquals(SkillVerificationStatus.REJECTED, result.getFinalStatus());
        assertTrue(result.getBreakdown().getHardGatesTriggered().contains("HG-05_EXPIRED_CERTIFICATE"));
    }

    @Test
    @DisplayName("Hard Gate HG-02: Tài liệu CV hoặc ảnh cá nhân (SELFIE) -> REJECT")
    void evaluate_CvOrSelfie_Rejects() {
        SkillVerificationAiResultDto cvAi = SkillVerificationAiResultDto.builder()
                .documentType("CV")
                .documentReadable(true)
                .recipientName("Phạm Duy Hưng")
                .overallConfidence(0.90)
                .routingDecision(RoutingDecision.REJECT)
                .extractedSkills(List.of(baristaExtracted(0.90)))
                .build();

        TrustEvaluationResult cvResult = engine.evaluate("Phạm Duy Hưng", baristaSkill, List.of(baristaSkill), cvAi);
        assertEquals(SkillVerificationStatus.REJECTED, cvResult.getFinalStatus());
        assertEquals(DocumentAuthenticityStatus.IRRELEVANT_DOCUMENT, cvResult.getAuthenticityStatus());

        SkillVerificationAiResultDto selfieAi = SkillVerificationAiResultDto.builder()
                .documentType("SELFIE")
                .documentReadable(true)
                .overallConfidence(0.10)
                .routingDecision(RoutingDecision.REJECT)
                .build();
        TrustEvaluationResult selfieResult = engine.evaluate("Phạm Duy Hưng", baristaSkill, List.of(baristaSkill), selfieAi);
        assertEquals(SkillVerificationStatus.REJECTED, selfieResult.getFinalStatus());
    }

    @Test
    @DisplayName("Hard Gate HG-06: Ảnh mờ (documentReadable=false) -> REJECT kèm hướng dẫn chụp lại rõ nét (không kết luận giả mạo, không chuyển Admin)")
    void evaluate_BlurryDocument_RejectsWithActionableAdvice() {
        SkillVerificationAiResultDto blurryAi = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(false)
                .recipientName("Phạm Duy Hưng")
                .institution("Saigontourist")
                .riskFlags(List.of("BLURRY_IMAGE"))
                .overallConfidence(0.55)
                .routingDecision(RoutingDecision.REJECT)
                .extractedSkills(List.of(baristaExtracted(0.60)))
                .build();

        TrustEvaluationResult result = engine.evaluate("Phạm Duy Hưng", baristaSkill, List.of(baristaSkill), blurryAi);

        assertEquals(DocumentAuthenticityStatus.UNREADABLE, result.getAuthenticityStatus());
        assertEquals(SkillVerificationStatus.REJECTED, result.getFinalStatus());
        assertEquals(RoutingDecision.REJECT, result.getFinalRoutingDecision());
        assertNotEquals(DocumentAuthenticityStatus.TAMPERED, result.getAuthenticityStatus());
        assertTrue(result.getFinalReason().contains("chụp lại bản gốc rõ nét"));
    }

    @Test
    @DisplayName("Hard Gate HG-09: Confidence ngoài phạm vi [0..1] hoặc ngày hết hạn < ngày cấp -> chặn AUTO_APPROVE")
    void evaluate_InvalidConfidenceOrInconsistentDates_BlocksAutoApprove() {
        SkillVerificationAiResultDto badConfAi = SkillVerificationAiResultDto.builder()
                .documentType("CERTIFICATE")
                .documentReadable(true)
                .recipientName("Phạm Duy Hưng")
                .institution("Saigontourist")
                .issueDate("2025-10-01")
                .expiryDate("2024-01-01")
                .hasSeal(true)
                .hasSealOrSignature(true)
                .overallConfidence(1.85) // ngoài khoảng [0..1]
                .routingDecision(RoutingDecision.AUTO_APPROVE)
                .extractedSkills(List.of(baristaExtracted(0.95)))
                .build();

        TrustEvaluationResult result = engine.evaluate("Phạm Duy Hưng", baristaSkill, List.of(baristaSkill), badConfAi);

        assertEquals(1.0, result.getSanitizedConfidence());
        assertNotEquals(SkillVerificationStatus.APPROVED, result.getFinalStatus());
        assertTrue(result.getBreakdown().getHardGatesTriggered().contains("HG-09_INVALID_AI_CONFIDENCE"));
        assertTrue(result.getBreakdown().getHardGatesTriggered().contains("HG-05B_DATE_INCONSISTENCY"));
    }
}
