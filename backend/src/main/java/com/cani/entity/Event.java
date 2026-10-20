package com.cani.entity;

import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.EventStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eo_id", nullable = false)
    private EventOrganizer eventOrganizer;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "venue_address", columnDefinition = "TEXT")
    private String venueAddress;

    @Column(name = "venue_lat")
    private Double venueLat;

    @Column(name = "venue_lng")
    private Double venueLng;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 50)
    @Builder.Default
    private EventMode mode = EventMode.MATCHING;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "readiness_score")
    @Builder.Default
    private Integer readinessScore = 0;

    @Column(name = "total_shifts")
    @Builder.Default
    private Integer totalShifts = 0;

    @Column(name = "total_required_workers")
    @Builder.Default
    private Integer totalRequiredWorkers = 0;

    @Column(name = "total_confirmed_workers")
    @Builder.Default
    private Integer totalConfirmedWorkers = 0;

    @Column(name = "total_estimated_cost", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalEstimatedCost = BigDecimal.ZERO;

    @Column(name = "total_paid", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
