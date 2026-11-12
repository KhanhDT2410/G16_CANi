package com.cani.repository;

import com.cani.entity.NoShowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoShowRecordRepository extends JpaRepository<NoShowRecord, UUID>, JpaSpecificationExecutor<NoShowRecord> {

    boolean existsByShiftAssignment_AssignmentId(UUID assignmentId);

    Optional<NoShowRecord> findByShiftAssignment_AssignmentId(UUID assignmentId);

    long countByDetectedAtBetween(Instant fromDate, Instant toDate);

    long countByIsAccountLockedTrue();

    @Query("SELECT SUM(n.penaltyAmount) FROM NoShowRecord n")
    Long sumTotalPenaltyPoints();
}
