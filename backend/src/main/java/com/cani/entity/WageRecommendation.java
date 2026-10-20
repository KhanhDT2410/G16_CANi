package com.cani.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wage_recommendations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WageRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "recommendation_id", updatable = false, nullable = false)
    private UUID recommendationId;

    @Column(name = "city", length = 50)
    private String city;

    @Column(name = "job_type", length = 100)
    private String jobType;

    @Column(name = "time_slot", length = 50)
    private String timeSlot;

    @Column(name = "lead_time", length = 50)
    private String leadTime;

    @Column(name = "suggested_wage", nullable = false, precision = 12, scale = 2)
    private BigDecimal suggestedWage;

    @Column(name = "matched_row_id")
    private UUID matchedRowId;

    @Column(name = "fallback_level")
    @Builder.Default
    private Integer fallbackLevel = 0;

    @Column(name = "shift_id")
    private UUID shiftId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
