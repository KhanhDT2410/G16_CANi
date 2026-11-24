package com.cani.repository;

import com.cani.entity.DisputeRule;
import com.cani.entity.enums.DisputeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeRuleRepository extends JpaRepository<DisputeRule, UUID>, JpaSpecificationExecutor<DisputeRule> {

    List<DisputeRule> findByEnabledTrueOrderByCodeAscRepeatIndexAsc();

    List<DisputeRule> findByRuleTypeAndEnabledTrue(DisputeType ruleType);
}
