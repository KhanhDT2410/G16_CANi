package com.cani.entity;

import com.cani.entity.enums.DisputeOutcome;
import com.cani.entity.enums.DisputeStatus;
import com.cani.entity.enums.DisputeType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Tranh chấp giữa Worker và Event Organizer (SRS FE-11, UC-96 → UC-104).
 * Vòng đời: OPEN → INVESTIGATING (CC) → RESOLVED/UNVERIFIED → ESCALATED (7 ngày, BR-54)
 * → FINAL_RESOLVED (Admin, BR-68). Quyết định WORKER_FAULT gắn kèm −10 điểm RS (BR-55).
 */
@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID disputeId;

    @Column(name = "assignment_id")
    private UUID assignmentId;

    @Column(name = "complainant_id", nullable = false)
    private UUID complainantId;

    @Column(name = "respondent_id", nullable = false)
    private UUID respondentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false)
    private DisputeType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private String evidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "messages", columnDefinition = "jsonb")
    private String messages;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    @Builder.Default
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * Chuyên viên Customer Care được gán xử lý (UC-99: CC tự nhận / chuyển đơn).
     */
    @Column(name = "assigned_to")
    private UUID assignedTo;

    /**
     * Hạn xử lý theo SLA (BR-53: công nhận trong 1 ngày làm việc, quyết định trong 7 ngày làm việc).
     */
    @Column(name = "sla_deadline")
    private Instant slaDeadline;

    /**
     * Kết quả quyết định (UC-100 / UC-102).
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "outcome")
    private DisputeOutcome outcome;

    @Column(name = "decision_rationale", columnDefinition = "TEXT")
    private String decisionRationale;

    // ===== Escalation (UC-101, BR-54: khiếu nại quyết định CC trong 7 ngày) =====

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    @Column(name = "escalated_by")
    private UUID escalatedBy;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    // ===== Quyết định cuối của Admin (UC-102, BR-68: ràng buộc, không khiếu nại thêm) =====

    @Column(name = "final_decision_rationale", columnDefinition = "TEXT")
    private String finalDecisionRationale;

    @Column(name = "final_decided_by")
    private UUID finalDecidedBy;

    @Column(name = "final_decided_at")
    private Instant finalDecidedAt;

    /**
     * BR-55: đánh dấu đã khấu trừ −10 RS cho worker (tránh trừ 2 lần khi CC và Admin đều ra quyết định WORKER_FAULT).
     */
    @Column(name = "rs_penalty_applied")
    private Boolean rsPenaltyApplied = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
