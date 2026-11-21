package com.cani.entity;

import com.cani.entity.enums.DocumentAuthenticityStatus;
import com.cani.entity.enums.IdentityVerificationStatus;
import com.cani.entity.enums.IssuerVerificationStatus;
import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillVerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skill_verification_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillVerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Column(name = "job_type", length = 100)
    private String jobType;

    @Column(name = "document_url", columnDefinition = "TEXT")
    private String documentUrl;

    @Column(name = "document_name")
    private String documentName;

    @Column(name = "document_type", length = 100)
    private String documentType;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "identity_number", length = 50)
    private String identityNumber;

    @Column(name = "institution")
    private String institution;

    @Column(name = "certificate_number", length = 100)
    private String certificateNumber;

    @Column(name = "issue_date", length = 50)
    private String issueDate;

    @Column(name = "expiry_date", length = 50)
    private String expiryDate;

    @Column(name = "document_hash", length = 64)
    private String documentHash;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_raw_response", columnDefinition = "jsonb")
    private String aiRawResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_skills", columnDefinition = "jsonb")
    private String extractedSkills;

    @Column(name = "has_seal_or_signature")
    @Builder.Default
    private Boolean hasSealOrSignature = false;

    @Column(name = "overall_confidence", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal overallConfidence = BigDecimal.ZERO;

    @Column(name = "trust_score", precision = 5, scale = 2)
    private BigDecimal trustScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trust_score_breakdown", columnDefinition = "jsonb")
    private String trustScoreBreakdown;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "identity_verification_status", length = 40)
    private IdentityVerificationStatus identityVerificationStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "issuer_verification_status", length = 40)
    private IssuerVerificationStatus issuerVerificationStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "document_authenticity_status", length = 40)
    private DocumentAuthenticityStatus documentAuthenticityStatus;

    @Column(name = "verification_source", length = 100)
    private String verificationSource;

    @Column(name = "verification_reference", columnDefinition = "TEXT")
    private String verificationReference;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "routing_decision", nullable = false, length = 50)
    private RoutingDecision routingDecision;

    @Column(name = "routing_reason", columnDefinition = "TEXT")
    private String routingReason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private SkillVerificationStatus status = SkillVerificationStatus.PENDING_REVIEW;

    @Column(name = "human_reviewed", nullable = false)
    @Builder.Default
    private Boolean humanReviewed = false;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "worker_notes", columnDefinition = "TEXT")
    private String workerNotes;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "sp_awarded")
    @Builder.Default
    private Integer spAwarded = 0;

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
