package com.cani.entity;

import com.cani.entity.enums.ExperienceLevel;
import com.cani.entity.enums.ShiftStatus;
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
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID shiftId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "job_type", nullable = false, length = 100)
    private String jobType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "required_workers", nullable = false)
    @Builder.Default
    private Integer requiredWorkers = 1;

    @Column(name = "confirmed_workers")
    @Builder.Default
    private Integer confirmedWorkers = 0;

    @Column(name = "remaining_workers")
    @Builder.Default
    private Integer remainingWorkers = 1;

    @Column(name = "hourly_wage", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyWage;

    @Column(name = "allowance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal allowance = BigDecimal.ZERO;

    @Column(name = "total_cost", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    @Builder.Default
    private ShiftStatus status = ShiftStatus.DRAFT;

    @Column(name = "risk_score")
    @Builder.Default
    private Integer riskScore = 0;

    @Column(name = "location_address", columnDefinition = "TEXT")
    private String locationAddress;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lng")
    private Double locationLng;

    @Column(name = "required_skills", columnDefinition = "uuid[]")
    private UUID[] requiredSkills;

    @Column(name = "min_reliability_score")
    @Builder.Default
    private Integer minReliabilityScore = 0;

    @Column(name = "max_distance_km")
    private Double maxDistanceKm;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "min_experience_level")
    @Builder.Default
    private ExperienceLevel minExperienceLevel = ExperienceLevel.BEGINNER;

    @Column(name = "arrival_time")
    private Instant arrivalTime;

    @Column(name = "dress_code", columnDefinition = "TEXT")
    private String dressCode;

    @Column(name = "checklist", columnDefinition = "jsonb")
    private String checklist;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
