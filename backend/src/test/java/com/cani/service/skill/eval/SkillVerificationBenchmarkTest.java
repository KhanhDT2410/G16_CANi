package com.cani.service.skill.eval;

import com.cani.entity.enums.SkillVerificationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bộ kiểm thử Benchmark đánh giá tái sử dụng (Regression & Prompt Improvement Benchmark).
 * So sánh phiên bản Prompt v1.0.0 (Baseline) và v2.0.0 (Improved Grounded Extraction)
 * trên hai tập mẫu độc lập: Dev Set và Eval Set.
 */
@DisplayName("SkillVerification — Benchmark Suite & Prompt Version Comparison")
class SkillVerificationBenchmarkTest {

    private SkillEvaluationSuiteRunner runner;
    private SkillDocumentExtractor extractor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        extractor = new SkillDocumentExtractor(objectMapper);
        runner = new SkillEvaluationSuiteRunner(extractor, objectMapper);
    }

    @Test
    @DisplayName("Dev Set Benchmark (v2.0.0): Đạt 100% quyết định chính xác, 0 duyệt nhầm trên tập phát triển")
    void testDevSetBenchmark_PromptV2() {
        List<SkillEvaluationCase> devCases = runner.loadDataset("skill-eval/dev_dataset.json");
        assertEquals(10, devCases.size(), "Tập Dev phải có đúng 10 ca kiểm thử tối thiểu");

        SkillEvaluationReport report = runner.runEvaluation(devCases, SkillDocumentExtractor.PROMPT_V2, "synthetic-evaluator");

        System.out.println(report.generateMarkdownSummary());

        // Kiểm tra các chỉ số an toàn cốt lõi
        assertEquals(0, report.getWrongCertificatesApproved(), "TUYỆT ĐỐI KHÔNG được duyệt nhầm bất kỳ hồ sơ nào (0/8)");
        assertEquals(2, report.getValidCertificatesApproved(), "Số chứng chỉ hợp lệ được duyệt phải là 2/2");
        assertEquals(0, report.getValidCertificatesMissed(), "Không được bỏ sót chứng chỉ hợp lệ (0/2)");
        assertEquals(4, report.getUnverifiedCases(), "Số ca chưa thể xác minh (UNSUPPORTED_SOURCE + TEMPORARY_FAILURE + CV/File hỏng) là 4/10");
        assertEquals(10, report.getCorrectSpDecisions(), "Cả 10 ca phải quyết định mức SP hoàn toàn chính xác (10/10)");

        // Kiểm tra độ chính xác đọc trường của AI
        assertTrue(report.getFieldMetrics().get("documentType").getRate() >= 90.0);
        assertTrue(report.getFieldMetrics().get("recipientName").getRate() >= 90.0);
        assertTrue(report.getFieldMetrics().get("skills").getRate() >= 90.0);
        assertTrue(report.getFieldMetrics().get("evidenceSnippets").getRate() >= 90.0);
    }

    @Test
    @DisplayName("Eval Set Benchmark (Hold-out Test): So sánh phiên bản Prompt v1.0.0 vs v2.0.0")
    void testEvalSetBenchmark_PromptVersionComparison() {
        List<SkillEvaluationCase> evalCases = runner.loadDataset("skill-eval/eval_dataset.json");
        assertEquals(10, evalCases.size(), "Tập Eval giữ riêng phải có đúng 10 ca kiểm thử");

        // 1. Chạy trên phiên bản Baseline v1.0.0
        SkillEvaluationReport reportV1 = runner.runEvaluation(evalCases, SkillDocumentExtractor.PROMPT_V1, "baseline-model");

        // 2. Chạy trên phiên bản Cải tiến v2.0.0
        SkillEvaluationReport reportV2 = runner.runEvaluation(evalCases, SkillDocumentExtractor.PROMPT_V2, "grounded-model");

        System.out.println("========== SO SÁNH PHIÊN BẢN TRƯỚC VÀ SAU TRÊN TẬP EVAL ==========");
        System.out.println("--- PHIÊN BẢN v1.0.0 (BASELINE) ---");
        System.out.println(reportV1.generateMarkdownSummary());

        System.out.println("--- PHIÊN BẢN v2.0.0 (IMPROVED GROUNDED) ---");
        System.out.println(reportV2.generateMarkdownSummary());

        // Đo sự cải thiện (delta):
        // v1.0.0 thiếu grounding căn cứ (evidenceSnippets = 0%)
        // v2.0.0 bắt buộc căn cứ trích dẫn (evidenceSnippets = 90.0% vì 9/10 ca có căn cứ, ca tệp hỏng không bịa căn cứ)
        assertEquals(0.0, reportV1.getFieldMetrics().get("evidenceSnippets").getRate());
        assertEquals(90.0, reportV2.getFieldMetrics().get("evidenceSnippets").getRate(),
                "v2.0.0 trích xuất căn cứ chính xác 9/10 ca (ca tệp hỏng tuyệt đối không bịa đặt căn cứ)");

        // Cả 2 phiên bản khi kết hợp Backend Rules đều phải đảm bảo KHÔNG duyệt nhầm
        assertEquals(0, reportV1.getWrongCertificatesApproved());
        assertEquals(0, reportV2.getWrongCertificatesApproved());

        // Đúng 25 SP cho 2 chứng chỉ hợp lệ (Barista + ATTP vô thời hạn)
        assertEquals(2, reportV2.getValidCertificatesApproved());
        assertEquals(10, reportV2.getCorrectSpDecisions());
    }

    @Test
    @DisplayName("Regression Suite: Kiểm chứng toàn diện 7 ca tối thiểu + 2 ca từng lỗi trên nhánh hung")
    void testRegression_MinimalRequiredCases() {
        List<SkillEvaluationCase> allCases = runner.loadDataset("skill-eval/dev_dataset.json");

        // 1. CV tự khai -> 0 SP
        SkillEvaluationCase cvCase = findCase(allCases, "DEV-01-CV-SELF-DECLARED");
        var res1 = runner.simulateBackendVerification(cvCase);
        assertEquals(SkillVerificationStatus.REJECTED, res1.status());
        assertEquals(0, res1.spAwarded());

        // 2. CV / PDF hỏng -> 0 SP
        SkillEvaluationCase corruptCase = findCase(allCases, "DEV-02-CORRUPTED-PDF");
        var res2 = runner.simulateBackendVerification(corruptCase);
        assertEquals(SkillVerificationStatus.REJECTED, res2.status());
        assertEquals(0, res2.spAwarded());

        // 3. Chứng chỉ đúng người & đúng kỹ năng -> APPROVED, 25 SP
        SkillEvaluationCase validCase = findCase(allCases, "DEV-03-VALID-CERT");
        var res3 = runner.simulateBackendVerification(validCase);
        assertEquals(SkillVerificationStatus.APPROVED, res3.status());
        assertEquals(25, res3.spAwarded());

        // 4. Sai người -> REJECTED, 0 SP
        SkillEvaluationCase wrongPersonCase = findCase(allCases, "DEV-04-WRONG-PERSON");
        var res4 = runner.simulateBackendVerification(wrongPersonCase);
        assertEquals(SkillVerificationStatus.REJECTED, res4.status());
        assertEquals(0, res4.spAwarded());

        // 5. Sai kỹ năng -> REJECTED, 0 SP
        SkillEvaluationCase wrongSkillCase = findCase(allCases, "DEV-05-WRONG-SKILL");
        var res5 = runner.simulateBackendVerification(wrongSkillCase);
        assertEquals(SkillVerificationStatus.REJECTED, res5.status());
        assertEquals(0, res5.spAwarded());

        // 6. Hung Bug 2: Ngày hết hạn không đọc được / sai định dạng -> REJECTED, 0 SP
        SkillEvaluationCase badDateCase = findCase(allCases, "DEV-06-UNREADABLE-EXPIRY");
        var res6 = runner.simulateBackendVerification(badDateCase);
        assertEquals(SkillVerificationStatus.REJECTED, res6.status());
        assertEquals(0, res6.spAwarded());

        // 7. Hung Bug 1: Nguồn trả VERIFIED nhưng danh sách kỹ năng rỗng -> REJECTED, 0 SP
        SkillEvaluationCase noSkillCase = findCase(allCases, "DEV-07-NO-SKILL-FROM-SOURCE");
        var res7 = runner.simulateBackendVerification(noSkillCase);
        assertEquals(SkillVerificationStatus.REJECTED, res7.status());
        assertEquals(0, res7.spAwarded());

        // 8. Chứng chỉ vô thời hạn được nguồn xác nhận -> APPROVED, 25 SP
        SkillEvaluationCase permCase = findCase(allCases, "DEV-08-PERMANENT-VALID-CERT");
        var res8 = runner.simulateBackendVerification(permCase);
        assertEquals(SkillVerificationStatus.APPROVED, res8.status());
        assertEquals(25, res8.spAwarded());

        // 9. Nguồn cấp không hỗ trợ -> REJECTED, 0 SP
        SkillEvaluationCase unsupCase = findCase(allCases, "DEV-09-UNSUPPORTED-SOURCE");
        var res9 = runner.simulateBackendVerification(unsupCase);
        assertEquals(SkillVerificationStatus.REJECTED, res9.status());
        assertEquals(0, res9.spAwarded());

        // 10. Nguồn cấp gặp lỗi tạm thời -> VERIFICATION_FAILED, 0 SP
        SkillEvaluationCase tempFailCase = findCase(allCases, "DEV-10-TEMPORARY-FAILURE");
        var res10 = runner.simulateBackendVerification(tempFailCase);
        assertEquals(SkillVerificationStatus.VERIFICATION_FAILED, res10.status());
        assertEquals(0, res10.spAwarded());
    }

    private SkillEvaluationCase findCase(List<SkillEvaluationCase> cases, String id) {
        return cases.stream()
                .filter(c -> c.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không tìm thấy ca kiểm thử " + id));
    }
}
