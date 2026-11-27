package com.cani.entity;

import com.cani.entity.enums.AssignmentSource;
import com.cani.entity.enums.AssignmentStatus;
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
@Table(name = "shift_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID assignmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "proposal_id")
    private UUID proposalId;

    @Column(name = "invite_id")
    private UUID inviteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 50)
    @Builder.Default
    private AssignmentSource source = AssignmentSource.MATCHING;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "is_group_leader")
    @Builder.Default
    private Boolean isGroupLeader = false;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.PENDING;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_type", length = 50)
    private String cancellationType;

    @Column(name = "eo_confirmed")
    @Builder.Default
    private Boolean eoConfirmed = false;

    @Column(name = "eo_confirmed_at")
    private Instant eoConfirmedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist_progress", columnDefinition = "jsonb")
    private String checklistProgress;

    @Column(name = "confirmed_performed_skill_id")
    private UUID confirmedPerformedSkillId;

    @Column(name = "sp_experience_awarded", nullable = false)
    @Builder.Default
    private Boolean spExperienceAwarded = false;

    @Column(name = "sp_experience_awarded_at")
    private Instant spExperienceAwardedAt;

    @Column(name = "sp_rating_awarded", nullable = false)
    @Builder.Default
    private Boolean spRatingAwarded = false;

    @Column(name = "invite_override_reason", columnDefinition = "TEXT")
    private String inviteOverrideReason;

    @Column(name = "emergency_bonus", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal emergencyBonus = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
