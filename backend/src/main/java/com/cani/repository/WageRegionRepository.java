package com.cani.repository;

import com.cani.entity.WageRegion;
import com.cani.entity.enums.WageRegionCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WageRegionRepository extends JpaRepository<WageRegion, WageRegionCode> {
    List<WageRegion> findAllByOrderByCodeAsc();
}
