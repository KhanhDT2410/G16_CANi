package com.cani.entity;

import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.WageRegionCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "wage_matrix",
        uniqueConstraints = @UniqueConstraint(name = "uq_wage_matrix_region_tier", columnNames = {"region_code", "job_tier"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WageMatrix {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID matrixId;

    @Enumerated(EnumType.STRING)
    @Column(name = "region_code", nullable = false, length = 30)
    private WageRegionCode regionCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_tier", nullable = false, length = 30)
    private JobTier jobTier;

    @Column(name = "tier_label", nullable = false, length = 120)
    private String tierLabel;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "min_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal minMultiplier;

    @Column(name = "recommended_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal recommendedMultiplier;

    @Column(name = "max_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxMultiplier;

    @Column(name = "override_base_wage", precision = 12, scale = 2)
    private BigDecimal overrideBaseWage;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
