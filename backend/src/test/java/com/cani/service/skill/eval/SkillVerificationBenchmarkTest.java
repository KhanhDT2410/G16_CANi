package com.cani.service.skill.eval;

import com.cani.client.skill.CertificateLookupSource.CertificateLookupOutcome;
import com.cani.client.skill.OpenAiCompatibleSkillAiProvider;
import com.cani.client.skill.SkillAiProperties;
import com.cani.client.skill.UnsupportedCertificateLookupSource;
import com.cani.entity.enums.SkillVerificationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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

    @Test
    @DisplayName("Evidence Verbatim Test (AAU-2026-777): Tài liệu ghi 'Số hiệu', đầu ra ghi 'Mã hiệu' -> KHÔNG tính đạt trích dẫn")
    void testEvidenceSnippets_AAU2026777_SoHieuVsMaHieuFails() {
        List<SkillEvaluationCase> evalCases = runner.loadDataset("skill-eval/eval_dataset.json");
        SkillEvaluationCase baristaCase = findCase(evalCases, "EVAL-03-VALID-CERT-BARISTA");
        String docText = runner.loadSampleContent(baristaCase.getSampleDocumentPath());
        assertTrue(docText.contains("Số hiệu: AAU-2026-777"));

        DocumentExtractionResult wrongQuote = DocumentExtractionResult.builder()
                .status(DocumentExtractionResult.ExtractionStatus.SUCCESS)
                .documentType("CERTIFICATE")
                .recipientName("NGUYỄN VĂN AN")
                .institution("Trung tâm Đào tạo Ẩm thực Á Âu")
                .certificateNumber("AAU-2026-777")
                .expiryDate("31/12/2030")
                .extractedSkills(List.of("Pha chế (Bartender/Barista)"))
                .evidenceSnippets(Map.of("certificateNumber", "Mã hiệu: AAU-2026-777"))
                .build();

        assertFalse(runner.isEvidenceGrounded(docText, baristaCase.getGroundTruth(), wrongQuote),
                "Đầu ra tự đổi 'Số hiệu' thành 'Mã hiệu' không phải trích dẫn nguyên văn -> KHÔNG được tính đạt");

        DocumentExtractionResult exactQuote = DocumentExtractionResult.builder()
                .status(DocumentExtractionResult.ExtractionStatus.SUCCESS)
                .documentType("CERTIFICATE")
                .recipientName("NGUYỄN VĂN AN")
                .institution("Trung tâm Đào tạo Ẩm thực Á Âu")
                .certificateNumber("AAU-2026-777")
                .expiryDate("31/12/2030")
                .extractedSkills(List.of("Pha chế (Bartender/Barista)"))
                .evidenceSnippets(Map.of(
                        "recipientName", "Cấp cho: NGUYỄN VĂN AN",
                        "certificateNumber", "Số hiệu: AAU-2026-777",
                        "expiryDate", "Ngày hết hạn: 31/12/2030"
                ))
                .build();

        assertTrue(runner.isEvidenceGrounded(docText, baristaCase.getGroundTruth(), exactQuote),
                "Trích dẫn đúng nguyên văn 'Số hiệu: AAU-2026-777' phải ĐẠT");
    }

    @Test
    @DisplayName("Strict Rules Benchmark: 31/02/2030, hạn trống, thiếu eKYC -> 0 SP; nguồn xác nhận vô thời hạn -> 25 SP")
    void testStrictRules_31022030_BlankExpiry_MissingEkyc_Permanent() {
        SkillEvaluationCase baseValid = SkillEvaluationCase.builder()
                .id("TEST-BASE")
                .workerFullName("NGUYỄN VĂN AN")
                .workerEkycVerified(true)
                .workerRequestedSkill("Pha chế (Bartender/Barista)")
                .lookupOutcome(CertificateLookupOutcome.VERIFIED)
                .lookupSource("QR_OFFICIAL_REGISTRY")
                .lookupRecipientName("NGUYỄN VĂN AN")
                .lookupMatchedSkills(List.of("Pha chế (Bartender/Barista)"))
                .build();

        // 1. 31/02/2030 (Strict Date) -> REJECTED, 0 SP
        SkillEvaluationCase feb31 = SkillEvaluationCase.builder()
                .id("TEST-31-02")
                .workerFullName(baseValid.getWorkerFullName())
                .workerEkycVerified(true)
                .workerRequestedSkill(baseValid.getWorkerRequestedSkill())
                .lookupOutcome(CertificateLookupOutcome.VERIFIED)
                .lookupSource("QR_OFFICIAL_REGISTRY")
                .lookupRecipientName("NGUYỄN VĂN AN")
                .lookupExpiryDate("31/02/2030")
                .lookupMatchedSkills(baseValid.getLookupMatchedSkills())
                .build();
        var r1 = runner.simulateBackendVerification(feb31);
        assertEquals(SkillVerificationStatus.REJECTED, r1.status());
        assertEquals(0, r1.spAwarded());

        // 2. Hạn để trống mà nguồn chưa xác nhận vô thời hạn -> REJECTED, 0 SP
        SkillEvaluationCase blankExp = SkillEvaluationCase.builder()
                .id("TEST-BLANK-EXP")
                .workerFullName(baseValid.getWorkerFullName())
                .workerEkycVerified(true)
                .workerRequestedSkill(baseValid.getWorkerRequestedSkill())
                .lookupOutcome(CertificateLookupOutcome.VERIFIED)
                .lookupSource("QR_OFFICIAL_REGISTRY")
                .lookupRecipientName("NGUYỄN VĂN AN")
                .lookupExpiryDate("")
                .lookupPermanent(false)
                .lookupMatchedSkills(baseValid.getLookupMatchedSkills())
                .build();
        var r2 = runner.simulateBackendVerification(blankExp);
        assertEquals(SkillVerificationStatus.REJECTED, r2.status());
        assertEquals(0, r2.spAwarded());

        // 3. Thiếu eKYC đã xác thực -> REJECTED, 0 SP
        SkillEvaluationCase noEkyc = SkillEvaluationCase.builder()
                .id("TEST-NO-EKYC")
                .workerFullName("NGUYỄN VĂN AN")
                .workerEkycVerified(false)
                .workerRequestedSkill(baseValid.getWorkerRequestedSkill())
                .lookupOutcome(CertificateLookupOutcome.VERIFIED)
                .lookupSource("QR_OFFICIAL_REGISTRY")
                .lookupRecipientName("NGUYỄN VĂN AN")
                .lookupExpiryDate("31/12/2030")
                .lookupMatchedSkills(baseValid.getLookupMatchedSkills())
                .build();
        var r3 = runner.simulateBackendVerification(noEkyc);
        assertEquals(SkillVerificationStatus.REJECTED, r3.status());
        assertEquals(0, r3.spAwarded());

        // 4. Nguồn xác nhận vô thời hạn (lookupPermanent=true) dù expiryDate trống -> APPROVED, 25 SP
        SkillEvaluationCase confirmedPerm = SkillEvaluationCase.builder()
                .id("TEST-PERM-TRUE")
                .workerFullName("NGUYỄN VĂN AN")
                .workerEkycVerified(true)
                .workerRequestedSkill(baseValid.getWorkerRequestedSkill())
                .lookupOutcome(CertificateLookupOutcome.VERIFIED)
                .lookupSource("QR_OFFICIAL_REGISTRY")
                .lookupRecipientName("NGUYỄN VĂN AN")
                .lookupExpiryDate(null)
                .lookupPermanent(true)
                .lookupMatchedSkills(baseValid.getLookupMatchedSkills())
                .build();
        var r4 = runner.simulateBackendVerification(confirmedPerm);
        assertEquals(SkillVerificationStatus.APPROVED, r4.status());
        assertEquals(25, r4.spAwarded());
    }

    @Test
    @DisplayName("Config Safety: Có API key nhưng chưa xác thực kết nối / chạy mô phỏng -> vẫn báo SYNTHETIC_BENCHMARK_OFFLINE & UNCONFIGURED")
    void testConfiguredApiKeyAlone_DoesNotMarkLiveOrBreakOfflineMode() {
        SkillAiProperties props = new SkillAiProperties();
        props.setEnabled(true);
        props.setProvider("openai-compatible");
        props.setEndpoint("https://api.example.invalid/v1/chat/completions");
        props.setModel("gpt-4o-mini");
        props.setApiKey("sk-non-empty-unverified-key");
        props.setConnectionVerified(false);

        OpenAiCompatibleSkillAiProvider provider = new OpenAiCompatibleSkillAiProvider(props, objectMapper);
        SkillDocumentExtractor configuredKeyOnlyExtractor = new SkillDocumentExtractor(objectMapper, provider);
        SkillEvaluationSuiteRunner customRunner = new SkillEvaluationSuiteRunner(configuredKeyOnlyExtractor, objectMapper);

        // 1. Chuỗi apiKey không rỗng KHÔNG đủ để coi là sẵn sàng
        assertFalse(configuredKeyOnlyExtractor.isConfigured());

        // 2. Nếu cấu hình gemini phải bị từ chối rõ ràng
        SkillAiProperties geminiProps = new SkillAiProperties();
        geminiProps.setEnabled(true);
        geminiProps.setProvider("gemini");
        geminiProps.setEndpoint("https://generativelanguage.googleapis.com/v1beta/models");
        geminiProps.setModel("gemini-2.5-flash");
        geminiProps.setApiKey("AIzaSyFakeKey");
        OpenAiCompatibleSkillAiProvider geminiRejected = new OpenAiCompatibleSkillAiProvider(geminiProps, objectMapper);
        assertFalse(geminiRejected.hasValidConfiguration());
        assertTrue(geminiRejected.getUnconfiguredReason().contains("Gemini"));

        // 3. Không giải mã byte JPEG/PDF sang UTF-8 làm nội dung khi trích xuất thật
        byte[] fakeJpgWithText = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] payloadWithEmbeddedText = concat(fakeJpgWithText, "Trao cho: NGUYEN VAN AN\nKy nang: Pha che".getBytes(StandardCharsets.UTF_8));
        DocumentExtractionResult liveRes = new SkillDocumentExtractor(objectMapper, geminiRejected)
                .extract(payloadWithEmbeddedText, "cert.jpg", "image/jpeg", SkillDocumentExtractor.PROMPT_V2);
        assertEquals(DocumentExtractionResult.ExtractionStatus.UNCONFIGURED, liveRes.getStatus());
        assertTrue(liveRes.getExtractedSkills().isEmpty());

        // 4. Dù đã set apiKey nhưng chạy benchmark offline vẫn phải ghi nhãn SYNTHETIC_BENCHMARK_OFFLINE
        List<SkillEvaluationCase> evalCases = customRunner.loadDataset("skill-eval/eval_dataset.json");
        SkillEvaluationReport offlineReport = customRunner.runEvaluation(evalCases, SkillDocumentExtractor.PROMPT_V2, "gpt-4o-mini");
        assertEquals(SkillEvaluationReport.MODE_SYNTHETIC_OFFLINE, offlineReport.getExecutionMode());
        assertTrue(offlineReport.getLiveAiStatus().contains("CHƯA ĐÁNH GIÁ"));
        assertTrue(offlineReport.getLiveIssuerStatus().contains("CHƯA ĐÁNH GIÁ"));

        // 5. Lệnh so sánh Eval in ra đầy đủ từng ca & metrics
        String diffSummary = customRunner.comparePromptVersionsOnEval(
                evalCases, SkillDocumentExtractor.PROMPT_V1, SkillDocumentExtractor.PROMPT_V2, "synthetic-keyword-parser"
        );
        assertTrue(diffSummary.contains("EVAL-03-VALID-CERT"));
        assertTrue(diffSummary.contains("evidenceSnippets"));

        // 6. Kiểm chứng đầu cuối với UnsupportedCertificateLookupSource báo nguồn chưa đánh giá & 0 SP
        SkillEvaluationReport e2eReport = customRunner.runEndToEndVerification(
                evalCases, Map.of(), new UnsupportedCertificateLookupSource(), SkillDocumentExtractor.PROMPT_V2
        );
        assertEquals(SkillEvaluationReport.MODE_LIVE_END_TO_END, e2eReport.getExecutionMode());
        assertEquals(10, e2eReport.getUnverifiedCases());
        assertTrue(e2eReport.getLiveIssuerStatus().contains("CHƯA ĐÁNH GIÁ"));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
