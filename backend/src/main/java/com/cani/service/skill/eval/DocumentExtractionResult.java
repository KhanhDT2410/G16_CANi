package com.cani.service.skill.eval;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kết quả trích xuất tài liệu kỹ năng do AI xử lý.
 * AI chỉ trích xuất loại tài liệu, người nhận, đơn vị cấp, mã chứng chỉ,
 * ngày hết hạn và kỹ năng, kèm trích đoạn làm căn cứ khi có.
 * Trường thiếu hoặc không đọc được phải thể hiện rõ trong đầu ra.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentExtractionResult {

    public enum ExtractionStatus {
        SUCCESS,
        UNCONFIGURED,
        UNREADABLE_OR_CORRUPTED,
        PARSE_ERROR
    }

    @Builder.Default
    private ExtractionStatus status = ExtractionStatus.SUCCESS;

    private String documentType;       // "CERTIFICATE", "CV_SELF_DECLARED", "CORRUPTED", "UNKNOWN"
    private String recipientName;
    private String institution;
    private String certificateNumber;
    private String expiryDate;         // Ngày hết hạn dạng yyyy-MM-dd / dd/MM/yyyy hoặc "VO_THOI_HAN" / "KHONG_DOC_DUOC"

    @Builder.Default
    private List<String> extractedSkills = new ArrayList<>();

    @Builder.Default
    private Map<String, String> evidenceSnippets = new HashMap<>();

    @Builder.Default
    private List<String> unreadableFields = new ArrayList<>();

    @Builder.Default
    private List<String> missingFields = new ArrayList<>();

    private String promptVersion;
    private String modelName;
    private String rawResponse;
    private String failureMessage;

    public boolean isConfigured() {
        return status != ExtractionStatus.UNCONFIGURED;
    }

    public boolean isCorrupted() {
        return status == ExtractionStatus.UNREADABLE_OR_CORRUPTED
                || "CORRUPTED".equalsIgnoreCase(documentType);
    }
}
