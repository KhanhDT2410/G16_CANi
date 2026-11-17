package com.cani.repository;

import com.cani.entity.Plan;
import com.cani.entity.enums.PlanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findByPlanType(PlanType planType);
    Optional<Plan> findByPlanName(String planName);
}
