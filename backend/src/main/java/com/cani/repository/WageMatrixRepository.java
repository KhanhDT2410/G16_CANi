package com.cani.repository;

import com.cani.entity.WageMatrix;
import com.cani.entity.enums.JobTier;
import com.cani.entity.enums.WageRegionCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WageMatrixRepository extends JpaRepository<WageMatrix, UUID>, JpaSpecificationExecutor<WageMatrix> {

    Optional<WageMatrix> findByRegionCodeAndJobTier(WageRegionCode regionCode, JobTier jobTier);

    List<WageMatrix> findByJobTierAndIsActiveTrue(JobTier jobTier);

    List<WageMatrix> findByRegionCodeAndIsActiveTrue(WageRegionCode regionCode);
}
