package com.cani.entity;

import com.cani.entity.enums.AccountAppealStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Đơn giải trình / khiếu nại khi tài khoản bị Admin khóa (Locked Account Appeal).
 * Áp dụng cho Nhà tổ chức (Event Organizer) hoặc Người lao động (Worker) khi bị chuyển sang trạng thái LOCKED.
 */
@Entity
@Table(name = "account_appeals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountAppeal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "user_role", nullable = false, length = 50)
    private String userRole;

    @Column(name = "reason_category", nullable = false, length = 100)
    private String reasonCategory;

    @Column(name = "appeal_content", nullable = false, columnDefinition = "TEXT")
    private String appealContent;

    @Column(name = "attachment_urls", columnDefinition = "TEXT")
    private String attachmentUrls;

    @Column(name = "lock_reason_snapshot", columnDefinition = "TEXT")
    private String lockReasonSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private AccountAppealStatus status = AccountAppealStatus.SUBMITTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
