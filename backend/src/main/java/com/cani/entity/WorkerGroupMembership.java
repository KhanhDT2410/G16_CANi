package com.cani.entity;

import com.cani.entity.enums.GroupRole;
import com.cani.entity.enums.MembershipStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "worker_group_memberships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerGroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "membership_id", updatable = false, nullable = false)
    private UUID membershipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private WorkerGroup workerGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_in_group", length = 50)
    @Builder.Default
    private GroupRole roleInGroup = GroupRole.MEMBER;

    @Column(name = "role_title", length = 100)
    private String roleTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.PENDING;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
