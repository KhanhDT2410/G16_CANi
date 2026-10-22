package com.cani.entity;

import com.cani.entity.enums.ExperienceLevel;
import com.cani.entity.enums.VerificationTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** One verified/declared skill of a Worker (BR-16, BR-17). */
@Entity
@Table(name = "worker_skills",
        uniqueConstraints = @UniqueConstraint(name = "uq_worker_skills_worker_skill",
                columnNames = {"worker_id", "skill_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "proficiency", nullable = false)
    @Builder.Default
    private ExperienceLevel proficiency = ExperienceLevel.BEGINNER;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_tier", nullable = false, length = 20)
    @Builder.Default
    private VerificationTier verificationTier = VerificationTier.SELF_DECLARED;

    @Column(name = "sp_verify_awarded", nullable = false)
    @Builder.Default
    private Integer spVerifyAwarded = 0;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "evidence_request_id")
    private UUID evidenceRequestId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
