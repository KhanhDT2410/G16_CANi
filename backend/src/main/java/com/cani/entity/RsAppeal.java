package com.cani.entity;

import com.cani.entity.enums.RsAppealStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Đơn kháng cáo điểm uy tín Worker (SRS FE-10, UC-94: RS Penalty & Appeal).
 * Cho phép Worker nộp giải trình và bằng chứng bất khả kháng trong vòng 7 ngày kể từ khi bị trừ RS.
 * Quyết định phê duyệt sẽ khôi phục điểm RS và lưu nhật ký kiểm toán.
 */
@Entity
@Table(name = "rs_appeals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RsAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reliability_log_id", nullable = false, unique = true)
    private ReliabilityLog reliabilityLog;

    @Column(name = "penalty_amount", nullable = false)
    private Integer penaltyAmount;

    @Column(name = "reason_code", nullable = false, length = 100)
    private String reasonCode;

    @Column(name = "reason_detail", nullable = false, columnDefinition = "TEXT")
    private String reasonDetail;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RsAppealStatus status = RsAppealStatus.SUBMITTED;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}

