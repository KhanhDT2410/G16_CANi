package com.cani.entity;

import com.cani.entity.enums.PlanType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID planId;

    @Column(name = "plan_name", nullable = false, unique = true, length = 100)
    private String planName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "plan_type", nullable = false)
    private PlanType planType;

    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    private String features;

    @Column(name = "max_events")
    private Integer maxEvents;

    @Column(name = "max_shifts")
    private Integer maxShifts;

    @Column(name = "max_workers")
    private Integer maxWorkers;

    @Column(name = "base_matching_quota", nullable = false)
    @Builder.Default
    private Integer baseMatchingQuota = 0;
}
