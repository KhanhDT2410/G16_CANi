package com.cani.entity;

import com.cani.entity.enums.SkillRequirementType;
import com.cani.entity.enums.VerificationTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shift_skill_requirements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftSkillRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID requirementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "requirement_type", nullable = false, length = 20)
    @Builder.Default
    private SkillRequirementType requirementType = SkillRequirementType.REQUIRED;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "min_verification_tier", nullable = false, length = 30)
    @Builder.Default
    private VerificationTier minVerificationTier = VerificationTier.SELF_DECLARED;

    @Column(name = "requires_valid_certificate", nullable = false)
    @Builder.Default
    private Boolean requiresValidCertificate = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
