package com.cani.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "worker_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "group_id", updatable = false, nullable = false)
    private UUID groupId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "skill_focus", length = 100)
    private String skillFocus;

    @Column(name = "leader_worker_id")
    private UUID leaderWorkerId;

    @Column(name = "min_size")
    @Builder.Default
    private Integer minSize = 5;

    @Column(name = "max_size")
    @Builder.Default
    private Integer maxSize = 20;

    @Column(name = "availability", columnDefinition = "jsonb")
    private String availability;

    @Column(name = "member_count")
    @Builder.Default
    private Integer memberCount = 1;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "reliability_score")
    @Builder.Default
    private Integer reliabilityScore = 100;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
