package com.cani.entity;

import com.cani.entity.enums.InviteTargetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "invite_id", updatable = false, nullable = false)
    private UUID inviteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 50)
    @Builder.Default
    private InviteTargetType targetType = InviteTargetType.SHIFT;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "max_uses")
    @Builder.Default
    private Integer maxUses = 1;

    @Column(name = "used_count")
    @Builder.Default
    private Integer usedCount = 0;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
