package com.cani.repository;

import com.cani.entity.PayOSOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayOSOrderRepository extends JpaRepository<PayOSOrder, UUID> {
    Optional<PayOSOrder> findByOrderCode(Long orderCode);
    Optional<PayOSOrder> findByOrderCodeAndEoId(Long orderCode, UUID eoId);
}

