package com.cani.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eo_workers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EoWorker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID eoWorkerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eo_id", nullable = false)
    private EventOrganizer eventOrganizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Column(name = "total_shifts_together")
    @Builder.Default
    private Integer totalShiftsTogether = 0;

    @Column(name = "completion_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal completionRate = BigDecimal.ZERO;

    @Column(name = "avg_rating_given", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal avgRatingGiven = BigDecimal.ZERO;

    @Column(name = "last_worked_at")
    private Instant lastWorkedAt;

    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    @Column(name = "is_favorited")
    @Builder.Default
    private Boolean isFavorited = false;

    @Column(name = "is_blacklisted")
    @Builder.Default
    private Boolean isBlacklisted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
