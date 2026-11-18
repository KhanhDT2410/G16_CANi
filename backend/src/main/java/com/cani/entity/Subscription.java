package com.cani.entity;

import com.cani.entity.enums.PlanType;
import com.cani.entity.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID subscriptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    private EventOrganizer eventOrganizer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = true)
    private Plan plan;

    @Column(name = "plan_name", length = 100)
    private String planName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "plan_type")
    private PlanType planType;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "price_paid", precision = 15, scale = 2)
    private BigDecimal pricePaid;

    @Column(name = "max_events")
    private Integer maxEvents;

    @Column(name = "max_shifts")
    private Integer maxShifts;

    @Column(name = "max_workers")
    private Integer maxWorkers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    private String features;

    @Column(name = "events_used")
    @Builder.Default
    private Integer eventsUsed = 0;

    @Column(name = "shifts_used")
    @Builder.Default
    private Integer shiftsUsed = 0;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // =========================================================================
    // HELPER GETTERS (Tương thích ngược với các service, DTO và RevenueService)
    // =========================================================================
    public String getPlanName() {
        if (planName != null && !planName.isBlank()) {
            return planName;
        }
        return plan != null ? plan.getPlanName() : null;
    }

    public PlanType getPlanType() {
        if (planType != null) {
            return planType;
        }
        return plan != null ? plan.getPlanType() : null;
    }

    public BigDecimal getPrice() {
        if (pricePaid != null) return pricePaid;
        if (price != null) return price;
        return plan != null ? plan.getPrice() : BigDecimal.ZERO;
    }

    public Integer getMaxEvents() {
        if (maxEvents != null) return maxEvents;
        return (plan != null && plan.getMaxEvents() != null) ? plan.getMaxEvents() : 0;
    }

    public Integer getMaxShifts() {
        if (maxShifts != null) return maxShifts;
        return (plan != null && plan.getMaxShifts() != null) ? plan.getMaxShifts() : 0;
    }

    public Integer getMaxWorkers() {
        if (maxWorkers != null) return maxWorkers;
        return (plan != null && plan.getMaxWorkers() != null) ? plan.getMaxWorkers() : 0;
    }

    public Integer getDurationDays() {
        if (durationDays != null) return durationDays;
        return (plan != null && plan.getDurationDays() != null) ? plan.getDurationDays() : 0;
    }

    @Transient
    private String customFeatures;

    public String getFeatures() {
        if (customFeatures != null) return customFeatures;
        if (features != null && !features.isBlank()) return features;
        return plan != null ? plan.getFeatures() : null;
    }

    // =========================================================================
    // CUSTOM BUILDER (Cho phép các Unit Test cũ gọi .planType(...) / .maxEvents(...)
    // mà không bị compile error)
    // =========================================================================
    public static class SubscriptionBuilder {
        private Plan plan;
        private String planName;
        private PlanType planType;
        private Integer durationDays;
        private BigDecimal price;
        private BigDecimal pricePaid;
        private Integer maxEvents;
        private Integer maxShifts;
        private Integer maxWorkers;
        private String features;
        private String customFeatures;

        private void ensurePlan() {
            if (this.plan == null) {
                this.plan = Plan.builder().build();
            }
        }

        public SubscriptionBuilder plan(Plan plan) {
            this.plan = plan;
            if (plan != null) {
                if (this.planName == null) this.planName = plan.getPlanName();
                if (this.planType == null) this.planType = plan.getPlanType();
                if (this.durationDays == null) this.durationDays = plan.getDurationDays();
                if (this.price == null) this.price = plan.getPrice();
                if (this.maxEvents == null) this.maxEvents = plan.getMaxEvents();
                if (this.maxShifts == null) this.maxShifts = plan.getMaxShifts();
                if (this.maxWorkers == null) this.maxWorkers = plan.getMaxWorkers();
            }
            return this;
        }

        public SubscriptionBuilder planType(PlanType planType) {
            this.planType = planType;
            ensurePlan();
            this.plan.setPlanType(planType);
            return this;
        }

        public SubscriptionBuilder planName(String planName) {
            this.planName = planName;
            ensurePlan();
            this.plan.setPlanName(planName);
            return this;
        }

        public SubscriptionBuilder maxEvents(Integer maxEvents) {
            this.maxEvents = maxEvents;
            ensurePlan();
            this.plan.setMaxEvents(maxEvents);
            return this;
        }

        public SubscriptionBuilder maxShifts(Integer maxShifts) {
            this.maxShifts = maxShifts;
            ensurePlan();
            this.plan.setMaxShifts(maxShifts);
            return this;
        }

        public SubscriptionBuilder maxWorkers(Integer maxWorkers) {
            this.maxWorkers = maxWorkers;
            ensurePlan();
            this.plan.setMaxWorkers(maxWorkers);
            return this;
        }

        public SubscriptionBuilder durationDays(Integer durationDays) {
            this.durationDays = durationDays;
            ensurePlan();
            this.plan.setDurationDays(durationDays);
            return this;
        }

        public SubscriptionBuilder price(BigDecimal price) {
            this.price = price;
            this.pricePaid = price;
            ensurePlan();
            this.plan.setPrice(price);
            return this;
        }

        public SubscriptionBuilder pricePaid(BigDecimal pricePaid) {
            this.pricePaid = pricePaid;
            if (this.price == null) this.price = pricePaid;
            return this;
        }

        public SubscriptionBuilder features(String features) {
            this.features = features;
            this.customFeatures = features;
            ensurePlan();
            this.plan.setFeatures(features);
            return this;
        }
    }
}
