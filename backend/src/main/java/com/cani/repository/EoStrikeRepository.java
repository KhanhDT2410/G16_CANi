package com.cani.repository;

import com.cani.entity.EoStrike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EoStrikeRepository extends JpaRepository<EoStrike, UUID> {

    boolean existsByDispute_DisputeId(UUID disputeId);

    long countByEo_EoIdAndCreatedAtAfter(UUID eoId, Instant since);

    List<EoStrike> findByEo_EoIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID eoId, Instant since);
}
