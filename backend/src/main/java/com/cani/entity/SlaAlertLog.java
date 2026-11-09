package com.cani.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Bảng ghi nhận các cảnh báo quá hạn SLA đã được phát đi.
 * Đảm bảo tính Idempotent cho background job (không gửi lặp thông báo nhiều lần).
 */
@Entity
@Table(name = "sla_alert_logs", uniqueConstraints = {
        @UniqueConstraint(name = "uq_sla_alert_log", columnNames = {"entity_type", "entity_id", "breach_stage"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaAlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType; // "SUPPORT_TICKET", "DISPUTE"

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "breach_stage", nullable = false, length = 50)
    private String breachStage; // "TICKET_OVERDUE", "DISPUTE_CC_OVERDUE", "DISPUTE_ESCALATED_OVERDUE"

    @CreationTimestamp
    @Column(name = "alerted_at", updatable = false)
    private Instant alertedAt;
}

