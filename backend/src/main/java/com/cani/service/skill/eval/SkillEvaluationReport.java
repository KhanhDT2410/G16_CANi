package com.cani.service.skill.eval;

import com.cani.entity.enums.SkillVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Báo cáo chi tiết kết quả đánh giá hệ thống thẩm định kỹ năng.
 * Tách bạch rõ chế độ chạy (synthetic offline parser vs live AI thật vs live issuer),
 * báo kết quả theo từng ca, độ chính xác từng trường, và an toàn cấp SP.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEvaluationReport {

    public static final String MODE_SYNTHETIC_OFFLINE = "SYNTHETIC_BENCHMARK_OFFLINE";
    public static final String MODE_LIVE_AI_UNCONFIGURED = "LIVE_AI_UNCONFIGURED";
    public static final String MODE_LIVE_AI_CONNECTED = "LIVE_AI_CONNECTED";
    public static final String MODE_LIVE_END_TO_END = "LIVE_END_TO_END";

    private String datasetSplit;                 // "DEV" hoặc "EVAL"
    private String promptVersion;
    private String modelName;
    private String executionMode;                // MODE_*
    private String environmentNote;              // Ghi chú môi trường
    private String liveAiStatus;                 // Đã kiểm chứng thật hay CHƯA ĐÁNH GIÁ
    private String liveIssuerStatus;             // Đã kiểm chứng nguồn cấp thật hay CHƯA ĐÁNH GIÁ

    private int totalCases;
    private int evaluatedCases;

    @Builder.Default
    private List<CaseEvaluationResult> caseResults = new ArrayList<>();

    // Chỉ số đọc trường tài liệu của AI
    @Builder.Default
    private Map<String, FieldMetric> fieldMetrics = new LinkedHashMap<>();

    // Quyết định backend và an toàn cấp điểm SP
    private int validCertificatesApproved;       // True Positives (đúng người, đúng kỹ năng, hợp lệ được duyệt)
    private int totalValidCertificates;          // Tổng số chứng chỉ hợp lệ trong tập mẫu
    private int wrongCertificatesApproved;       // False Positives (DUYỆT NHẦM: sai người/sai kỹ năng/hết hạn nhưng bị duyệt)
    private int totalInvalidCases;               // Tổng số hồ sơ không hợp lệ trong tập mẫu
    private int validCertificatesMissed;         // False Negatives (chứng chỉ hợp lệ nhưng bị bỏ sót/từ chối)
    private int unverifiedCases;                 // Số ca chưa thể xác minh (UNSUPPORTED_SOURCE hoặc TEMPORARY_FAILURE)
    private int correctSpDecisions;              // Số ca quyết định đúng mức điểm SP (25 hoặc 0)

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseEvaluationResult {
        private String caseId;
        private String extractedDocumentType;
        private SkillVerificationStatus expectedStatus;
        private SkillVerificationStatus actualStatus;
        private int expectedSp;
        private int actualSp;
        private boolean evidenceGrounded;
        private boolean spDecisionCorrect;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldMetric {
        private int correct;
        private int total;

        public double getRate() {
            return total > 0 ? (correct * 100.0) / total : 0.0;
        }

        public String format() {
            return String.format("%d/%d (%.1f%%)", correct, total, getRate());
        }
    }

    public String formatRatio(int count, int denom) {
        double rate = denom > 0 ? (count * 100.0) / denom : 0.0;
        return String.format("%d/%d (%.1f%%)", count, denom, rate);
    }

    public String generateMarkdownSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("### Báo Cáo Đánh Giá Kỹ Năng [Tập: ").append(datasetSplit)
          .append(" | Prompt: ").append(promptVersion)
          .append(" | Model: ").append(modelName)
          .append(" | Mode: ").append(executionMode).append("]\n\n");

        sb.append("> **Môi trường & Điều kiện chạy**: ").append(environmentNote).append("\n");
        if (liveAiStatus != null && !liveAiStatus.isBlank()) {
            sb.append("> **Trạng thái AI thật**: ").append(liveAiStatus).append("\n");
        }
        if (liveIssuerStatus != null && !liveIssuerStatus.isBlank()) {
            sb.append("> **Trạng thái nguồn cấp thật**: ").append(liveIssuerStatus).append("\n");
        }
        sb.append("\n");

        sb.append("#### 1. Khả năng đọc tài liệu theo từng trường\n\n");
        sb.append("| Trường Dữ Liệu | Số ca đọc đúng / Tổng | Tỷ lệ chính xác |\n");
        sb.append("|---|:---:|:---:|\n");
        fieldMetrics.forEach((field, metric) -> {
            sb.append("| `").append(field).append("` | ")
              .append(metric.getCorrect()).append("/").append(metric.getTotal())
              .append(" | ").append(String.format("%.1f%%", metric.getRate())).append(" |\n");
        });

        sb.append("\n#### 2. Khả năng xác minh qua nguồn cấp và quyết định cấp SP\n\n");
        sb.append("- **Chứng chỉ hợp lệ được duyệt (True Positives)**: ")
          .append(formatRatio(validCertificatesApproved, totalValidCertificates)).append("\n");
        sb.append("- **Hồ sơ bị duyệt nhầm (False Positives - Vi phạm quy tắc)**: ")
          .append(formatRatio(wrongCertificatesApproved, totalInvalidCases))
          .append(wrongCertificatesApproved == 0 ? " *(An toàn 100%, không duyệt nhầm)*" : " *(CẢNH BÁO: Bị lọt hồ sơ)*")
          .append("\n");
        sb.append("- **Chứng chỉ hợp lệ bị bỏ sót (False Negatives)**: ")
          .append(formatRatio(validCertificatesMissed, totalValidCertificates)).append("\n");
        sb.append("- **Số ca chưa thể xác minh (Nguồn chưa hỗ trợ / Lỗi tạm thời)**: ")
          .append(unverifiedCases).append(" ca\n");
        sb.append("- **Độ chính xác cấp SP (Đúng 25 SP hoặc giữ 0 SP)**: ")
          .append(formatRatio(correctSpDecisions, totalCases)).append("\n");

        if (caseResults != null && !caseResults.isEmpty()) {
            sb.append("\n#### 3. Kết quả chi tiết theo từng ca\n\n");
            sb.append("| Ca kiểm thử | Loại TL | Trạng thái | SP (Thực/Kỳ vọng) | Trích dẫn nguyên văn |\n");
            sb.append("|---|---|---|:---:|:---:|\n");
            for (CaseEvaluationResult c : caseResults) {
                sb.append("| `").append(c.getCaseId()).append("` | ")
                  .append(c.getExtractedDocumentType()).append(" | ")
                  .append(c.getActualStatus()).append(" | ")
                  .append(c.getActualSp()).append("/").append(c.getExpectedSp()).append(" | ")
                  .append(c.isEvidenceGrounded() ? "ĐẠT" : "KHÔNG").append(" |\n");
            }
        }

        return sb.toString();
    }
}
