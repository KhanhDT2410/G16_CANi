package com.cani.repository;

import com.cani.entity.SlaAlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SlaAlertLogRepository extends JpaRepository<SlaAlertLog, UUID> {

    boolean existsByEntityTypeAndEntityIdAndBreachStage(String entityType, UUID entityId, String breachStage);
}

