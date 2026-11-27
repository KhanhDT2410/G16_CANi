package com.cani.entity;

import com.cani.entity.enums.WageRegionCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wage_regions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WageRegion {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 30)
    private WageRegionCode code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "legal_floor_hourly", nullable = false, precision = 12, scale = 2)
    private BigDecimal legalFloorHourly;

    @Column(name = "market_multiplier", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal marketMultiplier = BigDecimal.ONE;

    @Column(name = "description", length = 500)
    private String description;

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
