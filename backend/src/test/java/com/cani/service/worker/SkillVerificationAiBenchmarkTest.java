package com.cani.service.worker;

import com.cani.client.skill.SkillVisionAiClient;
import com.cani.dto.skill.ExtractedSkillDto;
import com.cani.dto.skill.SkillVerificationAiResultDto;
import com.cani.dto.skill.SkillVerificationResponseDto;
import com.cani.entity.Skill;
import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.Worker;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.repository.SkillRepository;
import com.cani.repository.SkillVerificationRequestRepository;
import com.cani.repository.WorkerRepository;
import com.cani.repository.WorkerSkillPointRepository;
import com.cani.repository.WorkerSkillRepository;
import com.cani.service.AuditLogService;
import com.cani.service.SkillVerificationService;
import com.cani.service.skill.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Deterministic Trust Engine & Hard-Gate Benchmark Suite (20 Golden Cases).
 * Đánh giá bộ quy tắc TrustScore + Hard Gates của Backend (không đánh đồng với đánh giá Vision Model thực tế).
 */
@ExtendWith(MockitoExtension.class)
public class SkillVerificationAiBenchmarkTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private WorkerSkillRepository workerSkillRepository;

    @Mock
    private WorkerSkillPointRepository workerSkillPointRepository;

    @Mock
    private SkillVerificationRequestRepository verificationRequestRepository;

    @Mock
    private SkillVisionAiClient skillVisionAiClient;

    @Mock
    private CertificateStorageService certificateStorageService;

    @Mock
    private AuditLogService auditLogService;

    private SkillVerificationService verificationService;

    private Worker defaultWorker;
    private Skill baristaSkill;

    private static byte[] validJpegBytes(int seed) {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, (byte) seed};
    }

    @BeforeEach
    void setUp() {
        verificationService = new SkillVerificationService(
                workerRepository,
                skillRepository,
                workerSkillRepository,
                workerSkillPointRepository,
                verificationRequestRepository,
                skillVisionAiClient,
                new ObjectMapper(),
                new FileSecurityValidator(),
                certificateStorageService,
                new DocumentAccessTokenService("benchmark-secret-key", 300),
                new SkillTrustScoringEngine(new AiOutputConsistencyValidator(), new TrustedIssuerRegistry()),
                auditLogService,
                null
        );

        defaultWorker = Worker.builder()
                .workerId(UUID.randomUUID())
                .fullName("Phạm Duy Hưng")
                .skillTier(SkillTier.STARTER)
                .build();

        baristaSkill = Skill.builder()
                .skillId(UUID.randomUUID())
                .name("Pha chế (Bartender/Barista)")
                .category("F&B")
                .isActive(true)
                .build();
    }

    record BenchmarkSample(
            String testCaseId,
            String description,
            String docType,
            boolean readable,
            String certName,
            String institution,
            boolean hasSeal,
            double rawConfidence,
            boolean isGroundTruthLegitimate
    ) {}

    @Test
    @DisplayName("Benchmark Suite V2: 20 Golden Cases cho Trust Scoring Engine & Hard Gates (FAR = 0%)")
    void runGoldenBenchmarkSuite() {
        List<BenchmarkSample> suite = List.of(
                // 1. Valid Trusted Certificates (8 cases - True Positives)
                new BenchmarkSample("TC-01", "Chứng chỉ Barista Saigontourist", "CERTIFICATE", true, "Phạm Duy Hưng", "Trường Trung cấp Du lịch Saigontourist", true, 0.95, true),
                new BenchmarkSample("TC-02", "Chứng chỉ Bếp Á Âu", "CERTIFICATE", true, "Phạm Duy Hưng", "Hướng Nghiệp Á Âu", true, 0.92, true),
                new BenchmarkSample("TC-03", "Chứng chỉ ĐH Văn Lang", "CERTIFICATE", true, "PHAM DUY HUNG", "Đại học Văn Lang", true, 0.90, true),
                new BenchmarkSample("TC-04", "Chứng nhận Bộ Công An", "CERTIFICATE", true, "Phạm Duy Hưng", "Bộ Công An", true, 0.96, true),
                new BenchmarkSample("TC-05", "Chứng chỉ VMA Academy", "CERTIFICATE", true, "Phạm Duy Hưng", "VMA Academy", true, 0.88, true),
                new BenchmarkSample("TC-06", "Chứng chỉ CĐ Du lịch Sài Gòn", "CERTIFICATE", true, "Phạm Hưng", "Cao đẳng Du lịch Sài Gòn", true, 0.85, true),
                new BenchmarkSample("TC-07", "Chứng nhận Event Club HCMC", "CERTIFICATE", true, "Pham Duy Hung", "Event Club HCMC", true, 0.84, true),
                new BenchmarkSample("TC-08", "Bằng Trường Trung cấp Nghề", "DIPLOMA", true, "Phạm Duy Hưng", "Trường Trung cấp Nghề TP.HCM", true, 0.89, true),

                // 2. Identity Mismatch (4 cases - Negative -> MUST NOT APPROVE)
                new BenchmarkSample("TC-09", "Mượn bằng bạn: Trần Văn An", "CERTIFICATE", true, "Trần Văn An", "Saigontourist", true, 0.95, false),
                new BenchmarkSample("TC-10", "Khác tên đệm: Phạm Minh Hưng", "CERTIFICATE", true, "Phạm Minh Hưng", "Hướng Nghiệp Á Âu", true, 0.93, false),
                new BenchmarkSample("TC-11", "Tên khác: Hoàng Quốc Việt", "CERTIFICATE", true, "Hoàng Quốc Việt", "Đại học FPT", true, 0.85, false),
                new BenchmarkSample("TC-12", "Lệch họ tên: Nguyễn Văn X", "CERTIFICATE", true, "Nguyễn Văn X", "VMA Academy", true, 0.92, false),

                // 3. Low Quality / Unreadable / Missing Seal (4 cases - Negative -> MUST NOT AUTO_APPROVE)
                new BenchmarkSample("TC-13", "Ảnh mờ góc xiên", "CERTIFICATE", false, "Phạm Duy Hưng", "Trường nghề", false, 0.55, false),
                new BenchmarkSample("TC-14", "Chứng chỉ thiếu mộc đỏ & chữ ký", "CERTIFICATE", true, "Phạm Duy Hưng", "Trung tâm chưa xác minh", false, 0.50, false),
                new BenchmarkSample("TC-15", "Scan đen trắng mất dấu", "CERTIFICATE", false, "Phạm Duy Hưng", "CLB Barista", false, 0.60, false),
                new BenchmarkSample("TC-16", "Ảnh bị lóa sáng che nội dung", "CERTIFICATE", false, null, "Hướng Nghiệp Á Âu", false, 0.45, false),

                // 4. Irrelevant / CV / Selfie / Meme (4 cases - Negative -> MUST REJECT)
                new BenchmarkSample("TC-17", "Ảnh selfie", "SELFIE", true, "Phạm Duy Hưng", null, false, 0.10, false),
                new BenchmarkSample("TC-18", "Hóa đơn tiền điện", "INVOICE", true, "Phạm Duy Hưng", "EVN", false, 0.15, false),
                new BenchmarkSample("TC-19", "CV tự khai", "CV", true, "Phạm Duy Hưng", null, false, 0.25, false),
                new BenchmarkSample("TC-20", "Ảnh meme", "MEME", true, null, null, false, 0.05, false)
        );

        when(workerRepository.findById(defaultWorker.getWorkerId())).thenReturn(Optional.of(defaultWorker));
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(baristaSkill));
        when(skillRepository.findById(baristaSkill.getSkillId())).thenReturn(Optional.of(baristaSkill));
        when(certificateStorageService.storeDocument(any(), anyString()))
                .thenReturn("/api/v1/workers/skills/documents/cert_bench.jpg");
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class))).thenAnswer(i -> {
            SkillVerificationRequest r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        int tp = 0;
        int fp = 0;
        int tn = 0;
        int fn = 0;
        int humanReview = 0;

        int idx = 0;
        for (BenchmarkSample sample : suite) {
            idx++;
            SkillVerificationAiResultDto aiResult = SkillVerificationAiResultDto.builder()
                    .documentType(sample.docType())
                    .documentReadable(sample.readable())
                    .recipientName(sample.certName())
                    .institution(sample.institution())
                    .issueDate("15/08/2025")
                    .hasSeal(sample.hasSeal())
                    .hasSealOrSignature(sample.hasSeal())
                    .overallConfidence(sample.rawConfidence())
                    .routingDecision(sample.rawConfidence() >= 0.80 ? RoutingDecision.AUTO_APPROVE : RoutingDecision.REJECT)
                    .routingReason(sample.description())
                    .extractedSkills(List.of(ExtractedSkillDto.builder()
                            .skillName("Barista")
                            .taxonomyRole("Pha chế (Bartender/Barista)")
                            .confidenceScore(sample.rawConfidence())
                            .build()))
                    .build();

            when(skillVisionAiClient.verifyDocument(any(), any(), anyList(), anyString(), anyString())).thenReturn(aiResult);

            MockMultipartFile file = new MockMultipartFile(
                    "file", sample.testCaseId() + ".jpg", "image/jpeg", validJpegBytes(idx));
            SkillVerificationResponseDto res = verificationService.submitVerification(
                    defaultWorker.getWorkerId(), file, baristaSkill.getSkillId(), null);

            boolean isApproved = (res.getStatus() == SkillVerificationStatus.APPROVED);
            if (res.getStatus() == SkillVerificationStatus.PENDING_REVIEW) {
                humanReview++;
            }

            if (sample.isGroundTruthLegitimate()) {
                if (isApproved) {
                    tp++;
                } else {
                    fn++;
                }
            } else {
                if (isApproved) {
                    fp++;
                } else {
                    tn++;
                }
            }
        }

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
        double far = (fp + tn) > 0 ? (double) fp / (fp + tn) : 0;

        System.out.printf("Trust Engine Benchmark V2 -> TP=%d/8, TN=%d/12 (HumanReview=%d), FP=%d, FN=%d | Precision=%.2f%%, Recall=%.2f%%, FAR=%.2f%%%n",
                tp, tn, humanReview, fp, fn, precision * 100, recall * 100, far * 100);

        assertEquals(0, fp, "False Acceptance Rate (FAR) must be ZERO!");
        assertEquals(8, tp, "All 8 valid trusted certificates must be approved!");
        assertEquals(12, tn, "All 12 invalid/mismatched/blurry cases must NOT be auto-approved!");
        assertEquals(0, humanReview, "Autonomous Engine: No cases should require human review!");
    }
}
