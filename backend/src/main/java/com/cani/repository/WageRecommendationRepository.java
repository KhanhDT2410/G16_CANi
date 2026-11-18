package com.cani.repository;

import com.cani.entity.WageRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WageRecommendationRepository extends JpaRepository<WageRecommendation, UUID> {

    Optional<WageRecommendation> findByShiftId(UUID shiftId);

    List<WageRecommendation> findByMatchedRowId(UUID matchedRowId);
}
