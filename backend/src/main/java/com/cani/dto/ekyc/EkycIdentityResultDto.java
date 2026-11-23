package com.cani.dto.ekyc;

import lombok.*;

import java.util.Map;

/**
 * DTO chuẩn hóa kết quả định danh điện tử trả về từ VNPT eKYC Provider.
 * Không tự suy đoán thang điểm, không bịa giá trị mặc định khi nhà cung cấp không trả dữ liệu.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EkycIdentityResultDto {

    public enum ExpiryDateStatus {
        VALID,
        EXPIRED,
        UNREADABLE
    }

    public enum FaceCompareStatus {
        MATCH,
        NOMATCH,
        BELOW_THRESHOLD,
        MISSING,
        TIMEOUT,
        INVALID_FORMAT,
        ERROR
    }

    public enum LivenessStatus {
        NOT_EVALUATED,
        PASSED,
        FAILED,
        ERROR
    }

    private String providerName;
    private String providerReference;

    private String idNumber;
    private String fullName;
    private String dob;
    private String gender;
    private String nationality;
    private String originAddress;
    private String residenceAddress;
    private String expiryDate;
    private String issueDate;
    private String identificationFeatures;

    /**
     * Độ tin cậy OCR thực tế nếu VNPT có trả về; null nếu không có (tuyệt đối không hardcode giả).
     */
    private Double ocrConfidence;

    /**
     * Điểm số Face Compare nguyên bản từ VNPT (không tự suy đoán thang 0-1 hay 0-100);
     * null nếu Face Compare lỗi, timeout, thiếu hoặc sai định dạng.
     */
    private Double faceMatchScore;

    /**
     * Thông điệp phân loại Face Compare nguyên bản từ VNPT (ví dụ: MATCH / NOMATCH).
     */
    private String faceCompareMessage;

    @Builder.Default
    private FaceCompareStatus faceCompareStatus = FaceCompareStatus.MISSING;

    @Builder.Default
    private ExpiryDateStatus expiryStatus = ExpiryDateStatus.UNREADABLE;

    @Builder.Default
    private LivenessStatus livenessStatus = LivenessStatus.NOT_EVALUATED;

    @Builder.Default
    private boolean documentLegal = true;

    private boolean fakeDocument;
    private boolean isCutCorner;
    private boolean isScreenCaptured;
    private boolean isPhotoshoped;
    private boolean isExpired;
    private boolean hasBlurryWarning;

    private boolean backCardVerified;
    private boolean backCardTampered;

    /**
     * True khi nguyên nhân là lỗi kỹ thuật (mạng, timeout, 401/403, 5xx, cấu hình thiếu, JSON hỏng).
     */
    private boolean technicalFailure;
    private String technicalErrorCode;

    private String routingDecision;
    private String routingReason;

    private Map<String, Object> rawAnalysis;
}
