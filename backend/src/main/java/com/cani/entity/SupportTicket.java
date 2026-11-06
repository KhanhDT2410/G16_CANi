package com.cani.entity;

import com.cani.entity.enums.TicketCategory;
import com.cani.entity.enums.TicketPriority;
import com.cani.entity.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Phiếu hỗ trợ khách hàng (SRS FE-12, UC-105 / UC-106 · SCR-25 / SCR-26).
 * Quy tắc SLA (BR-58):
 * - HIGH (BILLING, ACCOUNT): phản hồi 4h
 * - MEDIUM (TECHNICAL, GENERAL): phản hồi 1d
 * - LOW (OTHER): phản hồi 3d
 */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "reference_no", nullable = false, unique = true, length = 30)
    private String referenceNo;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "category", nullable = false)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "priority", nullable = false)
    private TicketPriority priority;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments", columnDefinition = "jsonb")
    private String attachments;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "sla_deadline", nullable = false)
    private Instant slaDeadline;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

