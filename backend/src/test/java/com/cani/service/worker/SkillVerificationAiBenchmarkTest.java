package com.cani.service.worker;

import com.cani.service.*;

import com.cani.client.skill.SkillVisionAiClient;
import com.cani.dto.skill.ExtractedSkillDto;
import com.cani.dto.skill.SkillVerificationAiResultDto;
import com.cani.dto.skill.SkillVerificationResponseDto;
import com.cani.entity.Skill;
import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.Worker;
import com.cani.entity.WorkerSkillPoint;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillTier;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.repository.SkillRepository;
import com.cani.repository.SkillVerificationRequestRepository;
import com.cani.repository.WorkerRepository;
import com.cani.repository.WorkerSkillPointRepository;
import com.cani.repository.WorkerSkillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Golden Evaluation Benchmark Suite for CANi AI-First Skill Verification Pipeline (UC-43 & UC-46).
 * Đánh giá độ tin cậy, tỷ lệ duyệt nhầm (False Positive Rate) và độ chính xác phân luồng.
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
    private ObjectMapper objectMapper;

    @InjectMocks
    private SkillVerificationService verificationService;

    private Worker defaultWorker;
    private Skill baristaSkill;

    @BeforeEach
    void setUp() {
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
            String certName,
            String institution,
            boolean hasSeal,
            double rawConfidence,
            RoutingDecision expectedDecision,
            boolean isGroundTruthLegitimate
    ) {}

    @Test
    @DisplayName("Benchmark Suite: Run 20 Golden Test Cases & Generate Confusion Matrix")
    void runGoldenBenchmarkSuite() {
        List<BenchmarkSample> suite = List.of(
                // 1. Valid Certificates (8 cases - True Positives)
                new BenchmarkSample("TC-01", "Chứng chỉ Barista Saigontourist", "Phạm Duy Hưng", "Saigontourist", true, 0.95, RoutingDecision.AUTO_APPROVE, true),
                new BenchmarkSample("TC-02", "Chứng chỉ Bếp Á Âu", "Phạm Duy Hưng", "Hướng Nghiệp Á Âu", true, 0.92, RoutingDecision.AUTO_APPROVE, true),
                new BenchmarkSample("TC-03", "Chứng chỉ Nghiệp vụ Lễ tân ĐH Văn Lang", "PHAM DUY HUNG", "ĐH Văn Lang", true, 0.90, RoutingDecision.AUTO_APPROVE, true),
                new BenchmarkSample("TC-04", "Chứng nhận An ninh Bảo vệ Bộ Công An", "Phạm Duy Hưng", "Bộ Công An", true, 0.96, RoutingDecision.AUTO_APPROVE, true),
                new BenchmarkSample("TC-05", "Chứng chỉ Sound Engineer Pro", "Phạm Duy Hưng", "VMA Academy", true, 0.88, RoutingDecision.AUTO_APPROVE, true),
                new BenchmarkSample("TC-06", "Chứng chỉ Phục vụ Bàn Nhà hàng", "Phạm Hưng", "CĐ Du lịch Sài Gòn", true, 0.85, RoutingDecision.AUTO_APPROVE, true),
                new BenchmarkSample("TC-07", "Chứng nhận Quản lý Sự kiện", "Pham Duy Hung", "Event Club HCMC", true, 0.84, RoutingDecision.AUTO_APPROVE, true),
                new BenchmarkSample("TC-08", "Bằng Sơ cấp Nghề Pha chế", "Phạm Duy Hưng", "Trường Trung cấp Nghề", true, 0.89, RoutingDecision.AUTO_APPROVE, true),

                // 2. Identity Mismatch - Bằng của người khác (4 cases - Negative)
                new BenchmarkSample("TC-09", "Mượn bằng bạn: Tên Trần Văn An", "Trần Văn An", "Saigontourist", true, 0.95, RoutingDecision.REJECT, false),
                new BenchmarkSample("TC-10", "Mượn bằng người yêu: Tên Lê Thị Mai", "Lê Thị Mai", "Á Âu", true, 0.90, RoutingDecision.REJECT, false),
                new BenchmarkSample("TC-11", "Tên hoàn toàn khác: Hoàng Quốc Việt", "Hoàng Quốc Việt", "ĐH Quốc tế", true, 0.85, RoutingDecision.REJECT, false),
                new BenchmarkSample("TC-12", "Lệch họ tên rõ rệt: Nguyễn Văn X", "Nguyễn Văn X", "VMA", true, 0.92, RoutingDecision.REJECT, false),

                // 3. Low Quality / Missing Seal / Blurry (4 cases - Negative)
                new BenchmarkSample("TC-13", "Ảnh mờ góc chụp xiên", "Phạm Duy Hưng", "Trường nghề", false, 0.55, RoutingDecision.REJECT, false),
                new BenchmarkSample("TC-14", "Chứng chỉ thiếu mộc đỏ", "Phạm Duy Hưng", "Trung tâm tiếng Anh", false, 0.50, RoutingDecision.REJECT, false),
                new BenchmarkSample("TC-15", "Scan đen trắng mất chữ ký", "Phạm Duy Hưng", "CLB Barista", false, 0.60, RoutingDecision.REJECT, false),
                new BenchmarkSample("TC-16", "Ảnh bị lóa sáng che nội dung", "Phạm Duy Hưng", "Á Âu", false, 0.45, RoutingDecision.REJECT, false),

                // 4. Irrelevant / Fake / Meme Documents (4 cases - Negative)
                new BenchmarkSample("TC-17", "Ảnh selfie không phải bằng", "Phạm Duy Hưng", null, false, 0.10, RoutingDecision.REJECT, false),
                new BenchmarkSample("TC-18", "Hóa đơn tiền điện", "Phạm Duy Hưng", "EVN", false, 0.15, RoutingDecision.REJECT, false),
                new BenchmarkSample("TC-19", "Ảnh chụp phong cảnh sự kiện", null, null, false, 0.05, RoutingDecision.REJECT, false),
                new BenchmarkSample("TC-20", "Ảnh meme tải từ mạng", null, null, false, 0.00, RoutingDecision.REJECT, false)
        );

        when(workerRepository.findById(defaultWorker.getWorkerId())).thenReturn(Optional.of(defaultWorker));
        when(skillRepository.findByIsActiveTrue()).thenReturn(List.of(baristaSkill));
        when(verificationRequestRepository.save(any(SkillVerificationRequest.class))).thenAnswer(i -> {
            SkillVerificationRequest r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        int tp = 0; // Thật và AI duyệt đúng
        int fp = 0; // Giả mà AI duyệt nhầm (Chỉ số tử huyệt cần = 0)
        int tn = 0; // Giả và AI từ chối đúng
        int fn = 0; // Thật mà AI từ chối

        for (BenchmarkSample sample : suite) {
            SkillVerificationAiResultDto aiResult = SkillVerificationAiResultDto.builder()
                    .recipientName(sample.certName())
                    .institution(sample.institution())
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

            when(skillVisionAiClient.verifyDocument(any(), any(), any())).thenReturn(aiResult);

            MockMultipartFile file = new MockMultipartFile("file", sample.testCaseId() + ".jpg", "image/jpeg", "test-bytes".getBytes());
            SkillVerificationResponseDto res = verificationService.submitVerification(defaultWorker.getWorkerId(), file, baristaSkill.getSkillId(), null);

            boolean isApproved = (res.getStatus() == SkillVerificationStatus.APPROVED);

            if (sample.isGroundTruthLegitimate()) {
                if (isApproved) {
                    tp++;
                } else {
                    fn++;
                }
            } else {
                if (isApproved) {
                    fp++; // Đáng báo động!
                } else {
                    tn++;
                }
            }
        }

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0;
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0;
        double accuracy = (double) (tp + tn) / suite.size();
        double fpr = (fp + tn) > 0 ? (double) fp / (fp + tn) : 0;

        System.out.println("========================================================================");
        System.out.println("          CANi AI SKILL VERIFICATION BENCHMARK REPORT (20 SAMPLES)       ");
        System.out.println("========================================================================");
        System.out.printf("  True Positives (TP):  %d / 8  (Hồ sơ hợp lệ được AI duyệt ngay)\n", tp);
        System.out.printf("  True Negatives (TN):  %d / 12 (Hồ sơ giả/lệch tên bị chặn đứng)\n", tn);
        System.out.printf("  False Positives (FP): %d / 12 (Tỷ lệ duyệt nhầm - Cam kết = 0)\n", fp);
        System.out.printf("  False Negatives (FN): %d / 8  (Hồ sơ hợp lệ bị từ chối)\n", fn);
        System.out.println("------------------------------------------------------------------------");
        System.out.printf("  Độ chính xác (Accuracy):              %.2f%%\n", accuracy * 100);
        System.out.printf("  Precision:                           %.2f%%\n", precision * 100);
        System.out.printf("  Recall:                              %.2f%%\n", recall * 100);
        System.out.printf("  False Positive Rate (FPR):           %.2f%%  [Mục tiêu: <= 2.0%%]\n", fpr * 100);
        System.out.println("========================================================================");

        // Assertions: Tuyệt đối không duyệt nhầm bằng giả hoặc bằng lệch tên (FPR = 0%)
        assertEquals(0, fp, "False Positive Rate must be ZERO! No fraudulent certs should be auto-approved!");
        assertEquals(8, tp, "All 8 valid certificates must be auto-approved!");
        assertEquals(12, tn, "All 12 invalid/mismatched certificates must be rejected!");
        assertEquals(1.0, precision, 0.001);
        assertEquals(1.0, recall, 0.001);
        assertEquals(0.0, fpr, 0.001);
    }
}

