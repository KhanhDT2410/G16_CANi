package com.cani.entity;

import com.cani.entity.enums.City;
import com.cani.entity.enums.LeadTime;
import com.cani.entity.enums.TimeSlot;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Column(name = "id", updatable = false, nullable = false)
    private UUID recommendationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "city")
    private City city;

    @Column(name = "job_type", length = 100)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "time_slot")
    private TimeSlot timeSlot;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "lead_time")
    private LeadTime leadTime;

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
