package com.cani.repository;

import com.cani.entity.Subscription;
import com.cani.entity.enums.PlanType;
import com.cani.entity.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByEventOrganizer_EoIdOrderByCreatedAtDesc(UUID eoId);

    @Query("SELECT s FROM Subscription s WHERE s.eventOrganizer.eoId = :eoId AND s.status = :status AND (s.expiresAt IS NULL OR s.expiresAt > :now) ORDER BY s.createdAt DESC")
    List<Subscription> findActiveSubscriptions(@Param("eoId") UUID eoId, @Param("status") SubscriptionStatus status, @Param("now") Instant now);

    Optional<Subscription> findFirstByEventOrganizer_EoIdAndStatusOrderByCreatedAtDesc(UUID eoId, SubscriptionStatus status);

    List<Subscription> findByCreatedAtBetween(Instant from, Instant to);

    Page<Subscription> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

    List<Subscription> findByPlanTypeAndCreatedAtBetween(PlanType planType, Instant from, Instant to);

    long countByCreatedAtBetween(Instant from, Instant to);

    long countByPlanTypeAndCreatedAtBetween(PlanType planType, Instant from, Instant to);

    @Query("SELECT COALESCE(SUM(s.price), 0) FROM Subscription s WHERE s.createdAt >= :from AND s.createdAt < :to")
    BigDecimal sumPriceBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COALESCE(SUM(s.price), 0) FROM Subscription s WHERE s.planType = :planType AND s.createdAt >= :from AND s.createdAt < :to")
    BigDecimal sumPriceByPlanTypeBetween(@Param("planType") PlanType planType, @Param("from") Instant from, @Param("to") Instant to);

    Page<Subscription> findByPlanTypeAndCreatedAtBetween(PlanType planType, Instant from, Instant to, Pageable pageable);
}


