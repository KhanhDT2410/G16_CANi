package com.cani.entity;

import com.cani.entity.enums.SkillTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "worker_skill_points")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerSkillPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "sp_id", updatable = false, nullable = false)
    private UUID spId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "job_type", nullable = false, length = 100)
    private String jobType;

    @Column(name = "sp_verify")
    @Builder.Default
    private Integer spVerify = 0;

    @Column(name = "sp_experience")
    @Builder.Default
    private Integer spExperience = 0;

    @Column(name = "sp_rating")
    @Builder.Default
    private Integer spRating = 0;

    @Column(name = "total_sp")
    @Builder.Default
    private Integer totalSp = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 50)
    @Builder.Default
    private SkillTier tier = SkillTier.STARTER;

    @Column(name = "total_shifts_completed")
    @Builder.Default
    private Integer totalShiftsCompleted = 0;

    @Column(name = "total_shifts_cancelled")
    @Builder.Default
    private Integer totalShiftsCancelled = 0;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
