package com.cani.dto.ekyc;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EoEkycVerificationResultDto {

    private UUID verificationId;
    private UUID eoId;
    private String companyName;
    private String representativeName;
    private String cccdNumberMasked;
    private String dob;
    private String gender;
    private String nationality;
    private String originAddress;
    private String residenceAddress;
    private String expiryDate;
    private String issueDate;

    /**
     * Signed URL ngắn hạn (chỉ cấp cho chủ sở hữu / Admin khi ảnh riêng tư còn hiệu lực).
     */
    private String frontImageUrl;
    private String backImageUrl;
    private String selfieImageUrl;

    private BigDecimal faceMatchScore;
    private BigDecimal ocrConfidence;
    private Map<String, Object> fraudFlags;

    private String provider;
    private String failureCode;
    private Boolean retryable;
    private Integer retryCount;
    private Boolean imagesAvailable;

    private String routingDecision;
    private String routingReason;
    private String status;

    private UUID reviewedBy;
    private String reviewNotes;
    private Instant createdAt;
    private Instant updatedAt;
}
