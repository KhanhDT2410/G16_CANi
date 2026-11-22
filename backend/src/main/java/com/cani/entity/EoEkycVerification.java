package com.cani.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "eo_ekyc_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EoEkycVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eo_id", nullable = false)
    private EventOrganizer eventOrganizer;

    @Column(name = "representative_name")
    private String representativeName;

    @Column(name = "cccd_number_masked", length = 50)
    private String cccdNumberMasked;

    @Column(name = "cccd_number_hash", length = 64)
    private String cccdNumberHash;

    @Column(name = "dob", length = 50)
    private String dob;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "nationality", length = 50)
    @Builder.Default
    private String nationality = "Việt Nam";

    @Column(name = "origin_address", columnDefinition = "TEXT")
    private String originAddress;

    @Column(name = "residence_address", columnDefinition = "TEXT")
    private String residenceAddress;

    @Column(name = "expiry_date", length = 50)
    private String expiryDate;

    @Column(name = "issue_date", length = 50)
    private String issueDate;

    /**
     * Lưu private objectKey trong kho lưu trữ bảo mật (KHÔNG lưu public URL hay URL giả).
     */
    @Column(name = "front_image_url", columnDefinition = "TEXT")
    private String frontImageUrl;

    @Column(name = "back_image_url", columnDefinition = "TEXT")
    private String backImageUrl;

    @Column(name = "selfie_image_url", columnDefinition = "TEXT")
    private String selfieImageUrl;

    @Column(name = "face_match_score", precision = 8, scale = 4)
    private BigDecimal faceMatchScore;

    @Column(name = "ocr_confidence", precision = 8, scale = 4)
    private BigDecimal ocrConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fraud_flags", columnDefinition = "jsonb")
    private Map<String, Object> fraudFlags;

    @Column(name = "provider", length = 50)
    @Builder.Default
    private String provider = "VNPT_EKYC";

    @Column(name = "provider_reference", length = 120)
    private String providerReference;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "image_retention_until")
    private Instant imageRetentionUntil;

    @Column(name = "images_purged_at")
    private Instant imagesPurgedAt;

    @Column(name = "routing_decision", length = 50, nullable = false)
    @Builder.Default
    private String routingDecision = "MANUAL_REVIEW";

    @Column(name = "routing_reason", columnDefinition = "TEXT")
    private String routingReason;

    @Column(name = "status", length = 50, nullable = false)
    @Builder.Default
    private String status = "PENDING"; // PENDING, PROCESSING, PENDING_REVIEW, APPROVED, REJECTED, TECHNICAL_FAILURE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
