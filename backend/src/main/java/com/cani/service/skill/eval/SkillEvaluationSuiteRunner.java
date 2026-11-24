package com.cani.service.skill.eval;

import com.cani.client.skill.CertificateLookupSource;
import com.cani.client.skill.CertificateLookupSource.CertificateLookupOutcome;
import com.cani.client.skill.CertificateLookupSource.CertificateLookupRequest;
import com.cani.client.skill.CertificateLookupSource.CertificateLookupResult;
import com.cani.entity.enums.SkillVerificationStatus;
import com.cani.service.SkillVerificationService;
import com.cani.service.skill.eval.DocumentExtractionResult.ExtractionStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;

/**
 * Bộ điều phối đánh giá chuẩn hóa (Benchmark Suite Runner) cho quy trình xác minh kỹ năng.
 * Tách bạch rõ 3 chế độ:
 * 1. {@link #runEvaluation}: chạy bộ đọc từ khóa tổng hợp (offline synthetic) + mô phỏng quy tắc backend.
 *    Kể cả khi cấu hình có chuỗi apiKey không rỗng, chế độ này KHÔNG bao giờ tự ghi nhận là LIVE_AI_CONNECTED.
 * 2. {@link #runLiveAiEvaluation}: gọi {@link SkillDocumentExtractor#extract} trực tiếp trên tệp ảnh/PDF;
 *    nếu provider chưa sẵn sàng -> báo LIVE_AI_UNCONFIGURED / CHƯA ĐÁNH GIÁ.
 * 3. {@link #runEndToEndVerification}: kiểm chứng đầu cuối qua {@link CertificateLookupSource} thật.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SkillEvaluationSuiteRunner {

    private static final List<DateTimeFormatter> STRICT_DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)
    );

    private final SkillDocumentExtractor documentExtractor;
    private final ObjectMapper objectMapper;

    public record BinarySample(String filename, String mimeType, byte[] bytes) {}

    public List<SkillEvaluationCase> loadDataset(String classpathLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            try (InputStream is = resource.getInputStream()) {
                return objectMapper.readValue(is, new TypeReference<List<SkillEvaluationCase>>() {});
            }
        } catch (Exception e) {
            throw new RuntimeException("Không thể nạp tập dữ liệu đánh giá từ " + classpathLocation + ": " + e.getMessage(), e);
        }
    }

    public String loadSampleContent(String relativePath) {
        try {
            ClassPathResource resource = new ClassPathResource("skill-eval/" + relativePath);
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Không tìm thấy tệp mẫu {}, sử dụng chuỗi mẫu mặc định: {}", relativePath, e.getMessage());
            return "SAMPLE_DOCUMENT_CONTENT [file=" + relativePath + "]";
        }
    }

    /**
     * Chạy benchmark offline bằng bộ đọc từ khóa tổng hợp (không gọi AI thật, không gọi nguồn cấp thật).
     * Kể cả khi có chuỗi apiKey trong cấu hình, chế độ trả về vẫn là SYNTHETIC_BENCHMARK_OFFLINE.
     */
    public SkillEvaluationReport runEvaluation(List<SkillEvaluationCase> cases, String promptVersion, String modelName) {
        String effectivePrompt = (promptVersion != null && !promptVersion.isBlank()) ? promptVersion : SkillDocumentExtractor.PROMPT_V2;
        String effectiveModel = (modelName != null && !modelName.isBlank()) ? modelName : "synthetic-keyword-parser";
        String split = (cases != null && !cases.isEmpty()) ? cases.get(0).getDatasetSplit() : "UNKNOWN";

        Map<String, SkillEvaluationReport.FieldMetric> fieldMetrics = initFieldMetrics();
        List<SkillEvaluationReport.CaseEvaluationResult> caseResults = new ArrayList<>();

        int validCertificatesApproved = 0;
        int totalValidCertificates = 0;
        int wrongCertificatesApproved = 0;
        int totalInvalidCases = 0;
        int validCertificatesMissed = 0;
        int unverifiedCases = 0;
        int correctSpDecisions = 0;

        for (SkillEvaluationCase testCase : cases) {
            String docText = loadSampleContent(testCase.getSampleDocumentPath());
            DocumentExtractionResult extraction = documentExtractor.extractSyntheticBenchmarkOnly(
                    docText, effectivePrompt, effectiveModel
            );

            boolean grounded = evaluateFieldExtraction(
                    testCase.getGroundTruth(), extraction, docText, fieldMetrics
            );

            BackendDecisionResult decision = simulateBackendVerification(testCase);

            boolean isExpectedApproved = testCase.getExpectedVerificationStatus() == SkillVerificationStatus.APPROVED;
            if (isExpectedApproved) {
                totalValidCertificates++;
                if (decision.status == SkillVerificationStatus.APPROVED) {
                    validCertificatesApproved++;
                } else {
                    validCertificatesMissed++;
                }
            } else {
                totalInvalidCases++;
                if (decision.status == SkillVerificationStatus.APPROVED) {
                    wrongCertificatesApproved++;
                }
            }

            if (decision.status == SkillVerificationStatus.VERIFICATION_FAILED
                    || "UNSUPPORTED_SOURCE".equals(decision.verificationSource)) {
                unverifiedCases++;
            }

            boolean spOk = decision.spAwarded == testCase.getExpectedSp();
            if (spOk) {
                correctSpDecisions++;
            }

            caseResults.add(SkillEvaluationReport.CaseEvaluationResult.builder()
                    .caseId(testCase.getId())
                    .extractedDocumentType(extraction.getDocumentType())
                    .expectedStatus(testCase.getExpectedVerificationStatus())
                    .actualStatus(decision.status())
                    .expectedSp(testCase.getExpectedSp())
                    .actualSp(decision.spAwarded())
                    .evidenceGrounded(grounded)
                    .spDecisionCorrect(spOk)
                    .note(decision.routingReason())
                    .build());
        }

        return SkillEvaluationReport.builder()
                .datasetSplit(split)
                .promptVersion(effectivePrompt)
                .modelName(effectiveModel)
                .executionMode(SkillEvaluationReport.MODE_SYNTHETIC_OFFLINE)
                .environmentNote("Chạy bộ đọc từ khóa tổng hợp (extractSyntheticBenchmarkOnly) và mô phỏng quy tắc backend trên tập mẫu.")
                .liveAiStatus("CHƯA ĐÁNH GIÁ (lần chạy này dùng bộ đọc từ khóa tổng hợp offline, không đi qua AI provider)")
                .liveIssuerStatus("CHƯA ĐÁNH GIÁ (dùng kết quả tra cứu giả lập trong dataset)")
                .totalCases(cases.size())
                .evaluatedCases(cases.size())
                .caseResults(caseResults)
                .fieldMetrics(fieldMetrics)
                .validCertificatesApproved(validCertificatesApproved)
                .totalValidCertificates(totalValidCertificates)
                .wrongCertificatesApproved(wrongCertificatesApproved)
                .totalInvalidCases(totalInvalidCases)
                .validCertificatesMissed(validCertificatesMissed)
                .unverifiedCases(unverifiedCases)
                .correctSpDecisions(correctSpDecisions)
                .build();
    }

    /**
     * So sánh 2 phiên bản prompt trên cùng một tập Eval và cùng model/cấu hình.
     */
    public String comparePromptVersionsOnEval(
            List<SkillEvaluationCase> evalCases,
            String baselinePrompt,
            String candidatePrompt,
            String modelName
    ) {
        SkillEvaluationReport before = runEvaluation(evalCases, baselinePrompt, modelName);
        SkillEvaluationReport after = runEvaluation(evalCases, candidatePrompt, modelName);
        StringBuilder sb = new StringBuilder();
        sb.append("## So Sánh Hai Phiên Bản Prompt Trên Tập Eval (Model: ").append(modelName).append(")\n\n");
        sb.append(before.generateMarkdownSummary()).append("\n");
        sb.append(after.generateMarkdownSummary()).append("\n");
        double evBefore = before.getFieldMetrics().get("evidenceSnippets").getRate();
        double evAfter = after.getFieldMetrics().get("evidenceSnippets").getRate();
        sb.append(String.format("### Delta (`%s` -> `%s`)\n", baselinePrompt, candidatePrompt));
        sb.append(String.format("- `evidenceSnippets`: %.1f%% -> %.1f%% (%+.1f%%)\n", evBefore, evAfter, evAfter - evBefore));
        sb.append(String.format("- `correctSpDecisions`: %d/%d -> %d/%d\n",
                before.getCorrectSpDecisions(), before.getTotalCases(),
                after.getCorrectSpDecisions(), after.getTotalCases()));
        return sb.toString();
    }

    /**
     * Chạy đánh giá trích xuất với AI thật trên tệp PDF/ảnh nhị phân qua {@link SkillDocumentExtractor#extract}.
     * Chỉ ghi nhận LIVE_AI_CONNECTED khi toàn bộ các ca thực sự đi qua provider AI thật (không UNCONFIGURED).
     */
    public SkillEvaluationReport runLiveAiEvaluation(
            List<SkillEvaluationCase> cases,
            Map<String, BinarySample> binarySamples,
            String promptVersion
    ) {
        String effectivePrompt = (promptVersion != null && !promptVersion.isBlank()) ? promptVersion : SkillDocumentExtractor.PROMPT_V2;
        String split = (cases != null && !cases.isEmpty()) ? cases.get(0).getDatasetSplit() : "UNKNOWN";

        Map<String, SkillEvaluationReport.FieldMetric> fieldMetrics = initFieldMetrics();
        List<SkillEvaluationReport.CaseEvaluationResult> caseResults = new ArrayList<>();
        int evaluatedThroughLiveAi = 0;
        String failureReason = null;

        for (SkillEvaluationCase testCase : cases) {
            BinarySample sample = binarySamples != null ? binarySamples.get(testCase.getId()) : null;
            if (sample == null) {
                failureReason = "Thiếu tệp nhị phân JPG/PNG/WEBP/PDF cho ca " + testCase.getId();
                continue;
            }
            DocumentExtractionResult extraction = documentExtractor.extract(
                    sample.bytes(), sample.filename(), sample.mimeType(), effectivePrompt
            );
            if (extraction.getStatus() == ExtractionStatus.UNCONFIGURED) {
                failureReason = extraction.getFailureMessage();
                break;
            }
            evaluatedThroughLiveAi++;
            String sampleText = loadSampleContent(testCase.getSampleDocumentPath());
            boolean grounded = evaluateFieldExtraction(testCase.getGroundTruth(), extraction, sampleText, fieldMetrics);
            caseResults.add(SkillEvaluationReport.CaseEvaluationResult.builder()
                    .caseId(testCase.getId())
                    .extractedDocumentType(extraction.getDocumentType())
                    .expectedStatus(testCase.getExpectedVerificationStatus())
                    .actualStatus(SkillVerificationStatus.REJECTED)
                    .expectedSp(testCase.getExpectedSp())
                    .actualSp(0)
                    .evidenceGrounded(grounded)
                    .spDecisionCorrect(false)
                    .note("Live AI extraction only")
                    .build());
        }

        boolean liveConnected = evaluatedThroughLiveAi == cases.size() && cases.size() > 0 && failureReason == null;
        return SkillEvaluationReport.builder()
                .datasetSplit(split)
                .promptVersion(effectivePrompt)
                .modelName(liveConnected ? documentExtractor.getConfiguredModelName() : "NONE")
                .executionMode(liveConnected ? SkillEvaluationReport.MODE_LIVE_AI_CONNECTED : SkillEvaluationReport.MODE_LIVE_AI_UNCONFIGURED)
                .environmentNote(liveConnected
                        ? "Đã gọi trực tiếp provider AI thực tế (" + documentExtractor.getConfiguredProviderName() + ")."
                        : "Không thể chạy Live AI: " + (failureReason != null ? failureReason : documentExtractor.getUnconfiguredReason()))
                .liveAiStatus(liveConnected
                        ? "ĐÃ KIỂM CHỨNG với " + documentExtractor.getConfiguredProviderName() + " / " + documentExtractor.getConfiguredModelName()
                        : "CHƯA ĐÁNH GIÁ (" + (failureReason != null ? failureReason : documentExtractor.getUnconfiguredReason()) + ")")
                .liveIssuerStatus("CHƯA ĐÁNH GIÁ (runLiveAiEvaluation chỉ đo phần trích xuất AI)")
                .totalCases(cases.size())
                .evaluatedCases(evaluatedThroughLiveAi)
                .caseResults(caseResults)
                .fieldMetrics(fieldMetrics)
                .build();
    }

    /**
     * Kiểm chứng đầu cuối qua {@link CertificateLookupSource} thật.
     * Nếu source hiện tại trả UNSUPPORTED_SOURCE, đánh dấu nguồn cấp thật là CHƯA ĐÁNH GIÁ.
     */
    public SkillEvaluationReport runEndToEndVerification(
            List<SkillEvaluationCase> cases,
            Map<String, BinarySample> binarySamples,
            CertificateLookupSource lookupSource,
            String promptVersion
    ) {
        String effectivePrompt = (promptVersion != null && !promptVersion.isBlank()) ? promptVersion : SkillDocumentExtractor.PROMPT_V2;
        String split = (cases != null && !cases.isEmpty()) ? cases.get(0).getDatasetSplit() : "UNKNOWN";

        List<SkillEvaluationReport.CaseEvaluationResult> caseResults = new ArrayList<>();
        int unverified = 0;
        int evaluated = 0;
        boolean anySupportedIssuer = false;

        for (SkillEvaluationCase testCase : cases) {
            BinarySample sample = binarySamples != null ? binarySamples.get(testCase.getId()) : null;
            byte[] bytes = sample != null ? sample.bytes() : new byte[0];
            String filename = sample != null ? sample.filename() : testCase.getSampleDocumentPath();
            String mimeType = sample != null ? sample.mimeType() : "application/pdf";

            CertificateLookupResult lookup = lookupSource.verifyCertificate(CertificateLookupRequest.builder()
                    .workerId(UUID.randomUUID())
                    .workerFullName(testCase.getWorkerFullName())
                    .documentName(filename)
                    .mimeType(mimeType)
                    .fileBytes(bytes)
                    .requestedSkillName(testCase.getWorkerRequestedSkill())
                    .build());

            evaluated++;
            if (lookup.getOutcome() == CertificateLookupOutcome.UNSUPPORTED_SOURCE
                    || lookup.getOutcome() == CertificateLookupOutcome.TEMPORARY_FAILURE) {
                unverified++;
            } else {
                anySupportedIssuer = true;
            }
            caseResults.add(SkillEvaluationReport.CaseEvaluationResult.builder()
                    .caseId(testCase.getId())
                    .extractedDocumentType(testCase.getGroundTruth() != null ? testCase.getGroundTruth().getDocumentType() : "UNKNOWN")
                    .expectedStatus(testCase.getExpectedVerificationStatus())
                    .actualStatus(lookup.getOutcome() == CertificateLookupOutcome.TEMPORARY_FAILURE
                            ? SkillVerificationStatus.VERIFICATION_FAILED
                            : SkillVerificationStatus.REJECTED)
                    .expectedSp(testCase.getExpectedSp())
                    .actualSp(0)
                    .evidenceGrounded(false)
                    .spDecisionCorrect(0 == testCase.getExpectedSp())
                    .note(lookup.getReason())
                    .build());
        }

        return SkillEvaluationReport.builder()
                .datasetSplit(split)
                .promptVersion(effectivePrompt)
                .modelName(documentExtractor.isConfigured() ? documentExtractor.getConfiguredModelName() : "NONE")
                .executionMode(SkillEvaluationReport.MODE_LIVE_END_TO_END)
                .environmentNote("Chạy kiểm chứng qua CertificateLookupSource: " + lookupSource.getClass().getSimpleName())
                .liveAiStatus(documentExtractor.isConfigured()
                        ? "ĐÃ CẤU HÌNH (" + documentExtractor.getConfiguredModelName() + ")"
                        : "CHƯA ĐÁNH GIÁ (" + documentExtractor.getUnconfiguredReason() + ")")
                .liveIssuerStatus(anySupportedIssuer
                        ? "ĐÃ KIỂM CHỨNG với " + lookupSource.getClass().getSimpleName()
                        : "CHƯA ĐÁNH GIÁ (chưa tích hợp nguồn cấp chứng chỉ chính thức - giữ UNSUPPORTED_SOURCE & 0 SP)")
                .totalCases(cases.size())
                .evaluatedCases(evaluated)
                .caseResults(caseResults)
                .fieldMetrics(initFieldMetrics())
                .unverifiedCases(unverified)
                .build();
    }

    private Map<String, SkillEvaluationReport.FieldMetric> initFieldMetrics() {
        Map<String, SkillEvaluationReport.FieldMetric> fieldMetrics = new LinkedHashMap<>();
        fieldMetrics.put("documentType", new SkillEvaluationReport.FieldMetric(0, 0));
        fieldMetrics.put("recipientName", new SkillEvaluationReport.FieldMetric(0, 0));
        fieldMetrics.put("institution", new SkillEvaluationReport.FieldMetric(0, 0));
        fieldMetrics.put("certificateNumber", new SkillEvaluationReport.FieldMetric(0, 0));
        fieldMetrics.put("expiryDate", new SkillEvaluationReport.FieldMetric(0, 0));
        fieldMetrics.put("skills", new SkillEvaluationReport.FieldMetric(0, 0));
        fieldMetrics.put("evidenceSnippets", new SkillEvaluationReport.FieldMetric(0, 0));
        return fieldMetrics;
    }

    private boolean evaluateFieldExtraction(
            SkillEvaluationCase.GroundTruth gt,
            DocumentExtractionResult act,
            String originalDocText,
            Map<String, SkillEvaluationReport.FieldMetric> metrics
    ) {
        if (gt == null || act == null) return false;

        metrics.get("documentType").setTotal(metrics.get("documentType").getTotal() + 1);
        if (equalsIgnoreCaseSafe(gt.getDocumentType(), act.getDocumentType())) {
            metrics.get("documentType").setCorrect(metrics.get("documentType").getCorrect() + 1);
        }

        if (gt.getRecipientName() != null) {
            metrics.get("recipientName").setTotal(metrics.get("recipientName").getTotal() + 1);
            if (equalsIgnoreCaseSafe(gt.getRecipientName(), act.getRecipientName())
                    || SkillVerificationService.isNameMatching(gt.getRecipientName(), act.getRecipientName())) {
                metrics.get("recipientName").setCorrect(metrics.get("recipientName").getCorrect() + 1);
            }
        }

        if (gt.getInstitution() != null) {
            metrics.get("institution").setTotal(metrics.get("institution").getTotal() + 1);
            if (equalsIgnoreCaseSafe(gt.getInstitution(), act.getInstitution())) {
                metrics.get("institution").setCorrect(metrics.get("institution").getCorrect() + 1);
            }
        }

        if (gt.getCertificateNumber() != null) {
            metrics.get("certificateNumber").setTotal(metrics.get("certificateNumber").getTotal() + 1);
            if (equalsIgnoreCaseSafe(gt.getCertificateNumber(), act.getCertificateNumber())) {
                metrics.get("certificateNumber").setCorrect(metrics.get("certificateNumber").getCorrect() + 1);
            }
        }

        if (gt.getExpiryDate() != null) {
            metrics.get("expiryDate").setTotal(metrics.get("expiryDate").getTotal() + 1);
            if (equalsIgnoreCaseSafe(gt.getExpiryDate(), act.getExpiryDate())) {
                metrics.get("expiryDate").setCorrect(metrics.get("expiryDate").getCorrect() + 1);
            }
        }

        if (gt.getSkills() != null && !gt.getSkills().isEmpty()) {
            metrics.get("skills").setTotal(metrics.get("skills").getTotal() + 1);
            boolean matchedAll = true;
            for (String expectedSkill : gt.getSkills()) {
                boolean found = act.getExtractedSkills().stream()
                        .anyMatch(s -> SkillVerificationService.normalizeName(s)
                                .contains(SkillVerificationService.normalizeName(expectedSkill)));
                if (!found) {
                    matchedAll = false;
                    break;
                }
            }
            if (matchedAll) {
                metrics.get("skills").setCorrect(metrics.get("skills").getCorrect() + 1);
            }
        }

        metrics.get("evidenceSnippets").setTotal(metrics.get("evidenceSnippets").getTotal() + 1);
        boolean grounded = isEvidenceGrounded(originalDocText, gt, act);
        if (!"CORRUPTED".equalsIgnoreCase(gt.getDocumentType()) && grounded) {
            metrics.get("evidenceSnippets").setCorrect(metrics.get("evidenceSnippets").getCorrect() + 1);
        }
        return grounded;
    }

    /**
     * Đối chiếu từng trích đoạn evidenceSnippets với nội dung gốc (nguyên văn)
     * và xác nhận trích đoạn có hỗ trợ đúng trường được chấm.
     * Ví dụ: tài liệu ghi "Số hiệu: AAU-2026-777", đầu ra ghi "Mã hiệu: AAU-2026-777" -> KHÔNG ĐẠT.
     */
    public boolean isEvidenceGrounded(
            String originalDocText,
            SkillEvaluationCase.GroundTruth gt,
            DocumentExtractionResult act
    ) {
        if (gt == null || act == null) {
            return false;
        }
        Map<String, String> actualSnippets = act.getEvidenceSnippets();
        if ("CORRUPTED".equalsIgnoreCase(gt.getDocumentType())) {
            return actualSnippets == null || actualSnippets.isEmpty();
        }
        if (actualSnippets == null || actualSnippets.isEmpty()) {
            return false;
        }
        if (originalDocText == null || originalDocText.isBlank()) {
            return false;
        }

        String normalizedDoc = collapseWhitespace(originalDocText);
        for (Map.Entry<String, String> entry : actualSnippets.entrySet()) {
            String field = entry.getKey();
            String snippet = entry.getValue();
            if (snippet == null || snippet.isBlank()) {
                return false;
            }
            // Kiểm tra trích dẫn nguyên văn có tồn tại trong nội dung tài liệu gốc
            if (!normalizedDoc.contains(collapseWhitespace(snippet))) {
                return false;
            }
            // Kiểm tra trích đoạn có thực sự chứa giá trị hỗ trợ cho đúng trường đó
            if (!doesSnippetSupportField(field, snippet, gt)) {
                return false;
            }
        }

        if (gt.getCertificateNumber() != null) {
            String certSnippet = actualSnippets.get("certificateNumber");
            if (certSnippet == null || !doesSnippetSupportField("certificateNumber", certSnippet, gt)) {
                return false;
            }
        }
        return true;
    }

    private boolean doesSnippetSupportField(
            String field,
            String snippet,
            SkillEvaluationCase.GroundTruth gt
    ) {
        String normSnippet = SkillVerificationService.normalizeName(snippet);
        return switch (field) {
            case "certificateNumber" -> gt.getCertificateNumber() == null
                    || snippet.toLowerCase(Locale.ROOT).contains(gt.getCertificateNumber().toLowerCase(Locale.ROOT));
            case "recipientName" -> gt.getRecipientName() == null
                    || normSnippet.contains(SkillVerificationService.normalizeName(gt.getRecipientName()));
            case "institution" -> gt.getInstitution() == null
                    || normSnippet.contains(SkillVerificationService.normalizeName(gt.getInstitution()));
            case "expiryDate" -> {
                if (gt.getExpiryDate() == null) yield true;
                if ("VO_THOI_HAN".equalsIgnoreCase(gt.getExpiryDate())) {
                    yield normSnippet.contains("vo thoi han") || normSnippet.contains("vinh vien") || normSnippet.contains("permanent");
                }
                if ("KHONG_DOC_DUOC".equalsIgnoreCase(gt.getExpiryDate())) {
                    yield normSnippet.contains("het han") || normSnippet.contains("chua ro") || normSnippet.contains("13/40");
                }
                yield snippet.toLowerCase(Locale.ROOT).contains(gt.getExpiryDate().toLowerCase(Locale.ROOT));
            }
            case "skills" -> {
                if (gt.getSkills() == null || gt.getSkills().isEmpty()) yield true;
                yield gt.getSkills().stream().anyMatch(skill -> {
                    String normSkill = SkillVerificationService.normalizeName(skill);
                    String primaryToken = normSkill.split("\\s+")[0];
                    return normSnippet.contains(normSkill) || (!primaryToken.isBlank() && normSnippet.contains(primaryToken));
                });
            }
            default -> true;
        };
    }

    private String collapseWhitespace(String input) {
        return input.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
    }

    private boolean equalsIgnoreCaseSafe(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    public record BackendDecisionResult(
            SkillVerificationStatus status,
            int spAwarded,
            String verificationSource,
            String routingReason
    ) {}

    /**
     * Chạy quy tắc thẩm định của backend đồng bộ với {@link SkillVerificationService}:
     * - Yêu cầu eKYC đã xác thực khớp người nhận (thiếu eKYC -> 0 SP)
     * - Parse ngày hết hạn ở chế độ STRICT (31/02/2030 -> 0 SP)
     * - Hạn để trống khi nguồn chưa xác nhận vô thời hạn -> 0 SP
     * - Chứng chỉ vô thời hạn chỉ hợp lệ khi nguồn xác nhận lookupPermanent = true
     */
    public BackendDecisionResult simulateBackendVerification(SkillEvaluationCase testCase) {
        CertificateLookupOutcome outcome = testCase.getLookupOutcome();
        if (outcome == null || outcome == CertificateLookupOutcome.UNSUPPORTED_SOURCE) {
            return new BackendDecisionResult(
                    SkillVerificationStatus.REJECTED,
                    0,
                    "UNSUPPORTED_SOURCE",
                    "Chưa thể xác minh tự động: Chứng chỉ không có nguồn tra cứu chính thức được CANi hỗ trợ."
            );
        }

        if (outcome == CertificateLookupOutcome.TEMPORARY_FAILURE) {
            return new BackendDecisionResult(
                    SkillVerificationStatus.VERIFICATION_FAILED,
                    0,
                    "ISSUER_API",
                    "Nguồn tra cứu chứng chỉ đang gặp sự cố tạm thời. Vui lòng thử lại sau ít phút."
            );
        }

        if (outcome == CertificateLookupOutcome.MISMATCH) {
            return new BackendDecisionResult(
                    SkillVerificationStatus.REJECTED,
                    0,
                    testCase.getLookupSource(),
                    "Thông tin từ nguồn tra cứu chứng chỉ không khớp với hồ sơ Worker."
            );
        }

        // Bắt buộc danh tính eKYC đã xác thực
        String workerName = testCase.getWorkerFullName();
        boolean hasVerifiedEkyc = testCase.getWorkerEkycVerified() != null
                ? testCase.getWorkerEkycVerified() && workerName != null && !workerName.isBlank()
                : workerName != null && !workerName.isBlank();
        if (!hasVerifiedEkyc) {
            return new BackendDecisionResult(
                    SkillVerificationStatus.REJECTED,
                    0,
                    testCase.getLookupSource(),
                    "Worker chưa có danh tính eKYC đã xác thực nên chưa được cấp điểm SP."
            );
        }

        String recipient = testCase.getLookupRecipientName();
        if (!SkillVerificationService.isNameMatching(workerName, recipient)) {
            return new BackendDecisionResult(
                    SkillVerificationStatus.REJECTED,
                    0,
                    testCase.getLookupSource(),
                    "Tên người nhận trên nguồn tra cứu chứng chỉ (" + recipient + ") không khớp với danh tính eKYC (" + workerName + ")."
            );
        }

        // Kiểm tra hạn hiệu lực ở chế độ STRICT
        boolean isPermanent = Boolean.TRUE.equals(testCase.getLookupPermanent());
        String rawExpiry = testCase.getLookupExpiryDate();
        if (!isPermanent) {
            if (rawExpiry == null || rawExpiry.isBlank()) {
                return new BackendDecisionResult(
                        SkillVerificationStatus.REJECTED,
                        0,
                        testCase.getLookupSource(),
                        "Nguồn tra cứu chưa cung cấp ngày hết hạn và chưa xác nhận chứng chỉ vô thời hạn."
                );
            }
            if (SkillVerificationService.isPermanentExpiry(rawExpiry)) {
                return new BackendDecisionResult(
                        SkillVerificationStatus.REJECTED,
                        0,
                        testCase.getLookupSource(),
                        "Chứng chỉ ghi vô thời hạn nhưng chưa có xác nhận rõ từ nguồn tra cứu chính thức."
                );
            }
            LocalDate parsed = parseStrictDate(rawExpiry);
            if (parsed == null) {
                return new BackendDecisionResult(
                        SkillVerificationStatus.REJECTED,
                        0,
                        testCase.getLookupSource(),
                        "Ngày hết hạn từ nguồn tra cứu chứng chỉ không hợp lệ theo chế độ STRICT (" + rawExpiry + ")."
                );
            }
            if (parsed.isBefore(LocalDate.now(ZoneOffset.UTC))) {
                return new BackendDecisionResult(
                        SkillVerificationStatus.REJECTED,
                        0,
                        testCase.getLookupSource(),
                        "Chứng chỉ đã hết hạn hiệu lực (" + rawExpiry + ")."
                );
            }
        }

        List<String> matchedSkills = testCase.getLookupMatchedSkills();
        if (matchedSkills == null || matchedSkills.isEmpty()) {
            return new BackendDecisionResult(
                    SkillVerificationStatus.REJECTED,
                    0,
                    testCase.getLookupSource(),
                    "Nguồn tra cứu chứng chỉ không xác nhận kỹ năng nào trong chứng chỉ."
            );
        }

        String reqSkill = testCase.getWorkerRequestedSkill();
        if (reqSkill != null && !reqSkill.isBlank()) {
            String normReq = SkillVerificationService.normalizeName(reqSkill);
            boolean skillConfirmed = matchedSkills.stream()
                    .anyMatch(s -> SkillVerificationService.normalizeName(s).contains(normReq)
                            || normReq.contains(SkillVerificationService.normalizeName(s)));
            if (!skillConfirmed) {
                return new BackendDecisionResult(
                        SkillVerificationStatus.REJECTED,
                        0,
                        testCase.getLookupSource(),
                        "Kỹ năng được nguồn tra cứu xác nhận không khớp với kỹ năng yêu cầu thẩm định (" + reqSkill + ")."
                );
            }
        }

        return new BackendDecisionResult(
                SkillVerificationStatus.APPROVED,
                25,
                testCase.getLookupSource(),
                "Chứng chỉ đã được xác minh tự động thành công qua nguồn tra cứu chính thức."
        );
    }

    private LocalDate parseStrictDate(String rawDate) {
        String trimmed = rawDate.trim();
        for (DateTimeFormatter fmt : STRICT_DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
