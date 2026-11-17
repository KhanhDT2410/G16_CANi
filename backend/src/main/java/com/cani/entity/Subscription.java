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

    @Column(name = "price_paid", precision = 15, scale = 2)
    private BigDecimal pricePaid;

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
    @Transient
    public String getPlanName() {
        return plan != null ? plan.getPlanName() : null;
    }

    @Transient
    public PlanType getPlanType() {
        return plan != null ? plan.getPlanType() : null;
    }

    @Transient
    public BigDecimal getPrice() {
        return pricePaid != null ? pricePaid : (plan != null ? plan.getPrice() : BigDecimal.ZERO);
    }

    @Transient
    public Integer getMaxEvents() {
        return plan != null ? plan.getMaxEvents() : 0;
    }

    @Transient
    public Integer getMaxShifts() {
        return plan != null ? plan.getMaxShifts() : 0;
    }

    @Transient
    public Integer getMaxWorkers() {
        return plan != null ? plan.getMaxWorkers() : 0;
    }

    @Transient
    public Integer getDurationDays() {
        return plan != null ? plan.getDurationDays() : 0;
    }

    @Transient
    private String customFeatures;

    @Transient
    public String getFeatures() {
        return customFeatures != null ? customFeatures : (plan != null ? plan.getFeatures() : null);
    }

    // =========================================================================
    // CUSTOM BUILDER (Cho phép các Unit Test cũ gọi .planType(...) / .maxEvents(...)
    // mà không bị compile error)
    // =========================================================================
    public static class SubscriptionBuilder {
        private Plan plan;
        private BigDecimal pricePaid;

        private void ensurePlan() {
            if (this.plan == null) {
                this.plan = Plan.builder().build();
            }
        }

        public SubscriptionBuilder plan(Plan plan) {
            this.plan = plan;
            return this;
        }

        public SubscriptionBuilder planType(PlanType planType) {
            ensurePlan();
            this.plan.setPlanType(planType);
            return this;
        }

        public SubscriptionBuilder planName(String planName) {
            ensurePlan();
            this.plan.setPlanName(planName);
            return this;
        }

        public SubscriptionBuilder maxEvents(Integer maxEvents) {
            ensurePlan();
            this.plan.setMaxEvents(maxEvents);
            return this;
        }

        public SubscriptionBuilder maxShifts(Integer maxShifts) {
            ensurePlan();
            this.plan.setMaxShifts(maxShifts);
            return this;
        }

        public SubscriptionBuilder maxWorkers(Integer maxWorkers) {
            ensurePlan();
            this.plan.setMaxWorkers(maxWorkers);
            return this;
        }

        public SubscriptionBuilder durationDays(Integer durationDays) {
            ensurePlan();
            this.plan.setDurationDays(durationDays);
            return this;
        }

        public SubscriptionBuilder price(BigDecimal price) {
            this.pricePaid = price;
            ensurePlan();
            this.plan.setPrice(price);
            return this;
        }

        public SubscriptionBuilder pricePaid(BigDecimal pricePaid) {
            this.pricePaid = pricePaid;
            return this;
        }

        private String customFeatures;

        public SubscriptionBuilder features(String features) {
            this.customFeatures = features;
            ensurePlan();
            this.plan.setFeatures(features);
            return this;
        }
    }
}
