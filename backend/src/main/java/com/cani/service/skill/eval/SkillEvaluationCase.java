package com.cani.service.skill.eval;

import com.cani.client.skill.CertificateLookupSource.CertificateLookupOutcome;
import com.cani.entity.enums.SkillVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Định dạng ca kiểm thử đánh giá tái sử dụng (Evaluation Case).
 * Mỗi ca có ID, tệp mẫu, kỹ năng Worker yêu cầu, đáp án các trường cần trích xuất,
 * căn cứ của đáp án, trạng thái xác minh mong đợi và SP mong đợi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEvaluationCase {

    private String id;                           // e.g. "DEV-01", "EVAL-01"
    private String datasetSplit;                 // "DEV" hoặc "EVAL"
    private String description;                  // Mô tả chi tiết kịch bản
    private String sampleDocumentPath;           // Đường dẫn tệp mẫu (tổng hợp/ẩn danh)
    private String workerFullName;               // Danh tính Worker
    private Boolean workerEkycVerified;          // null = suy ra theo workerFullName có giá trị; false = thiếu eKYC đã xác thực
    private String workerRequestedSkill;         // Kỹ năng Worker chọn trên UI

    // Ground Truth (Đáp án đã đối chiếu thực tế)
    private GroundTruth groundTruth;

    // Giả lập phản hồi từ nguồn tra cứu tin cậy (CertificateLookupSource)
    private CertificateLookupOutcome lookupOutcome;
    private String lookupSource;
    private String lookupRecipientName;
    private String lookupInstitution;
    private String lookupCertificateNumber;
    private String lookupExpiryDate;
    private Boolean lookupPermanent;
    @Builder.Default
    private List<String> lookupMatchedSkills = new ArrayList<>();
    private String lookupFailureReason;

    // Quyết định backend mong đợi
    private SkillVerificationStatus expectedVerificationStatus;
    private int expectedSp;
    private String expectedFailureCategory;      // "NONE", "AI_EXTRACTION", "LOOKUP_SOURCE", "BACKEND_RULE"

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroundTruth {
        private String documentType;             // "CERTIFICATE", "CV_SELF_DECLARED", "CORRUPTED"
        private String recipientName;
        private String institution;
        private String certificateNumber;
        private String expiryDate;               // dd/MM/yyyy hoặc "VO_THOI_HAN", "KHONG_DOC_DUOC"

        @Builder.Default
        private List<String> skills = new ArrayList<>();

        @Builder.Default
        private Map<String, String> evidenceSnippets = new HashMap<>();

        @Builder.Default
        private List<String> unreadableFields = new ArrayList<>();

        @Builder.Default
        private List<String> missingFields = new ArrayList<>();
    }
}
