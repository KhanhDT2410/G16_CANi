package com.cani.entity;

import com.cani.entity.enums.KycStatus;
import com.cani.entity.enums.SkillTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "worker_id", updatable = false, nullable = false)
    private UUID workerId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "cccd_number", length = 20)
    private String cccdNumber;

    @Column(name = "cccd_front_url", columnDefinition = "TEXT")
    private String cccdFrontUrl;

    @Column(name = "cccd_back_url", columnDefinition = "TEXT")
    private String cccdBackUrl;

    @Column(name = "selfie_url", columnDefinition = "TEXT")
    private String selfieUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status")
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "kyc_submitted_at")
    private Instant kycSubmittedAt;

    @Column(name = "kyc_reviewed_by")
    private UUID kycReviewedBy;

    @Column(name = "reliability_score")
    @Builder.Default
    private Integer reliabilityScore = 100;

    @Column(name = "rating_avg", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count")
    @Builder.Default
    private Integer ratingCount = 0;

    @Column(name = "total_shifts_completed")
    @Builder.Default
    private Integer totalShiftsCompleted = 0;

    @Column(name = "total_cancellations")
    @Builder.Default
    private Integer totalCancellations = 0;

    @Column(name = "total_noshows")
    @Builder.Default
    private Integer totalNoshows = 0;

    @Column(name = "is_online")
    @Builder.Default
    private Boolean isOnline = false;

    @Column(name = "base_address", columnDefinition = "TEXT")
    private String baseAddress;

    @Column(name = "base_lat")
    private Double baseLat;

    @Column(name = "base_lng")
    private Double baseLng;

    @Column(name = "skills", columnDefinition = "uuid[]")
    private UUID[] skills;

    @Column(name = "years_experience")
    @Builder.Default
    private Integer yearsExperience = 0;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "min_wage", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minWage = BigDecimal.ZERO;

    @Column(name = "max_distance_km")
    private Double maxDistanceKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_tier")
    @Builder.Default
    private SkillTier skillTier = SkillTier.STARTER;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
