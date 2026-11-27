package com.cani.repository;

import com.cani.entity.PayOSOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayOSOrderRepository extends JpaRepository<PayOSOrder, UUID> {
    Optional<PayOSOrder> findByOrderCode(Long orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PayOSOrder o WHERE o.orderCode = :orderCode")
    Optional<PayOSOrder> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);

    @Query(value = "SELECT nextval('payos_order_code_seq')", nativeQuery = true)
    Long nextOrderCode();

    List<PayOSOrder> findByCreatedAtBetween(Instant from, Instant to);
}

