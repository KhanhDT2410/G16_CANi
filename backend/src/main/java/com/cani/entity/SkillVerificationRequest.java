package com.cani.entity;

import com.cani.entity.enums.RoutingDecision;
import com.cani.entity.enums.SkillVerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @Column(name = "issue_date", length = 50)
    private String issueDate;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "extracted_skills", columnDefinition = "jsonb")
    private String extractedSkills;

    @Column(name = "has_seal_or_signature")
    @Builder.Default
    private Boolean hasSealOrSignature = false;

    @Column(name = "overall_confidence", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal overallConfidence = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_decision", nullable = false, length = 50)
    private RoutingDecision routingDecision;

    @Column(name = "routing_reason", columnDefinition = "TEXT")
    private String routingReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private SkillVerificationStatus status = SkillVerificationStatus.PENDING_REVIEW;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "worker_notes", columnDefinition = "TEXT")
    private String workerNotes;

    @Column(name = "sp_awarded")
    @Builder.Default
    private Integer spAwarded = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
