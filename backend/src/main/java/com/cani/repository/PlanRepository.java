package com.cani.repository;

import com.cani.entity.Plan;
import com.cani.entity.enums.PlanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findAllByPlanType(PlanType planType);
    Optional<Plan> findFirstByPlanType(PlanType planType);
    Optional<Plan> findByPlanName(String planName);
}
