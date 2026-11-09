package com.cani.entity;

import com.cani.entity.enums.NoShowDetectedBy;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "no_show_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoShowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false, unique = true)
    private ShiftAssignment shiftAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "detected_by", nullable = false)
    @Builder.Default
    private NoShowDetectedBy detectedBy = NoShowDetectedBy.SYSTEM_CRON;

    @Column(name = "detected_at")
    @Builder.Default
    private Instant detectedAt = Instant.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rs_penalty_log_id")
    private ReliabilityLog reliabilityLog;

    @Column(name = "penalty_amount", nullable = false)
    @Builder.Default
    private Integer penaltyAmount = 25;

    @Column(name = "is_account_locked", nullable = false)
    @Builder.Default
    private Boolean isAccountLocked = false;

    @Column(name = "emergency_dispatched", nullable = false)
    @Builder.Default
    private Boolean emergencyDispatched = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

