package com.cani.repository;

import com.cani.entity.ShiftAssignment;
import com.cani.entity.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {
    List<ShiftAssignment> findByShift_ShiftId(UUID shiftId);
    List<ShiftAssignment> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);
    List<ShiftAssignment> findByWorker_WorkerIdAndStatusIn(UUID workerId, List<AssignmentStatus> statuses);
    Optional<ShiftAssignment> findByShift_ShiftIdAndWorker_WorkerId(UUID shiftId, UUID workerId);
    boolean existsByShift_ShiftIdAndWorker_WorkerId(UUID shiftId, UUID workerId);

    @Query("SELECT sa FROM ShiftAssignment sa JOIN sa.shift s WHERE sa.worker.workerId = :workerId AND sa.status IN :activeStatuses AND s.startTime < :endTime AND s.endTime > :startTime")
    List<ShiftAssignment> findConflictingAssignments(
            @Param("workerId") UUID workerId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("activeStatuses") List<AssignmentStatus> activeStatuses
    );
}
