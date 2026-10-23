package com.cani.entity;

import com.cani.entity.enums.KycStatus;
import com.cani.entity.enums.SkillTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity đại diện cho Worker (Nhân sự sự kiện).
 * Bảng workers đã được chuẩn hóa 3NF:
 * - Dữ liệu eKYC chi tiết & hình ảnh chuyển sang bảng worker_ekyc_verifications.
 * - Cài đặt vị trí và mức lương tối thiểu được tách sang bảng con worker_preferences (@SecondaryTable).
 * - Kỹ năng chuyên biệt được quản lý qua bảng worker_skills.
 */
@Entity
@Table(name = "workers")
@SecondaryTable(name = "worker_preferences", pkJoinColumns = @PrimaryKeyJoinColumn(name = "worker_id", referencedColumnName = "id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID workerId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "cccd_number", length = 20)
    private String cccdNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "kyc_status")
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

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

    @Column(name = "years_experience")
    @Builder.Default
    private Integer yearsExperience = 0;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "skill_tier")
    @Builder.Default
    private SkillTier skillTier = SkillTier.STARTER;

    // -------------------------------------------------------------------------
    // CÁC THUỘC TÍNH THUỘC BẢNG worker_preferences (@SecondaryTable)
    // -------------------------------------------------------------------------
    @Column(table = "worker_preferences", name = "base_address", columnDefinition = "TEXT")
    private String baseAddress;

    @Column(table = "worker_preferences", name = "base_lat")
    private Double baseLat;

    @Column(table = "worker_preferences", name = "base_lng")
    private Double baseLng;

    @Column(table = "worker_preferences", name = "min_wage", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minWage = BigDecimal.ZERO;

    @Column(table = "worker_preferences", name = "max_distance_km")
    private Double maxDistanceKm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
