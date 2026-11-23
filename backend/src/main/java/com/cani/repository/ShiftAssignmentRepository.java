package com.cani.repository;

import com.cani.entity.ShiftAssignment;
import com.cani.entity.enums.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    List<ShiftAssignment> findByGroupId(UUID groupId);
    List<ShiftAssignment> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);
    List<ShiftAssignment> findByWorker_WorkerIdAndStatusIn(UUID workerId, List<AssignmentStatus> statuses);
    Optional<ShiftAssignment> findByShift_ShiftIdAndWorker_WorkerId(UUID shiftId, UUID workerId);
    boolean existsByShift_ShiftIdAndWorker_WorkerId(UUID shiftId, UUID workerId);
    boolean existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(UUID shiftId, UUID workerId, java.util.Collection<AssignmentStatus> statuses);

    @Query(value = "SELECT sa FROM ShiftAssignment sa " +
                   "JOIN FETCH sa.shift s " +
                   "LEFT JOIN FETCH s.event e " +
                   "WHERE sa.worker.workerId = :workerId " +
                   "AND (:status IS NULL OR sa.status = :status) " +
                   "AND sa.status IN :historyStatuses",
           countQuery = "SELECT COUNT(sa) FROM ShiftAssignment sa " +
                        "WHERE sa.worker.workerId = :workerId " +
                        "AND (:status IS NULL OR sa.status = :status) " +
                        "AND sa.status IN :historyStatuses")
    Page<ShiftAssignment> findHistoryAssignments(
            @Param("workerId") UUID workerId,
            @Param("status") AssignmentStatus status,
            @Param("historyStatuses") List<AssignmentStatus> historyStatuses,
            Pageable pageable
    );

    @Query("SELECT sa FROM ShiftAssignment sa JOIN sa.shift s WHERE sa.worker.workerId = :workerId AND sa.status IN :activeStatuses AND s.startTime < :endTime AND s.endTime > :startTime")
    List<ShiftAssignment> findConflictingAssignments(
            @Param("workerId") UUID workerId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("activeStatuses") List<AssignmentStatus> activeStatuses
    );

    @Query("SELECT sa FROM ShiftAssignment sa " +
           "JOIN FETCH sa.shift s " +
           "JOIN FETCH sa.worker w " +
           "JOIN FETCH w.user u " +
           "WHERE sa.status = :status " +
           "AND s.startTime <= :thresholdTime")
    List<ShiftAssignment> findPotentialNoShows(
            @Param("status") AssignmentStatus status,
            @Param("thresholdTime") Instant thresholdTime
    );

    // shortcut: dùng updatedAt của ShiftAssignment để xác định thời điểm no-show trong 20 phút qua; nâng cấp thêm trường noShowAt riêng khi có yêu cầu audit sâu hơn.
    @Query("SELECT DISTINCT sa.worker.workerId FROM ShiftAssignment sa WHERE sa.status = :status AND sa.updatedAt >= :since")
    List<UUID> findWorkerIdsByStatusAndUpdatedAtAfter(
            @Param("status") AssignmentStatus status,
            @Param("since") Instant since
    );

    @Query("SELECT sa FROM ShiftAssignment sa " +
           "JOIN FETCH sa.worker w " +
           "JOIN FETCH w.user u " +
           "JOIN FETCH sa.shift s " +
           "JOIN FETCH s.event e " +
           "WHERE e.eventOrganizer.eoId = :eoId " +
           "AND sa.status IN :statuses " +
           "ORDER BY sa.createdAt DESC")
    List<ShiftAssignment> findCollaboratedWorkersByEoId(
            @Param("eoId") UUID eoId,
            @Param("statuses") List<AssignmentStatus> statuses
    );

    @Query("SELECT sa FROM ShiftAssignment sa " +
           "JOIN FETCH sa.worker w " +
           "LEFT JOIN FETCH w.user wu " +
           "JOIN FETCH sa.shift s " +
           "JOIN FETCH s.event e " +
           "JOIN FETCH e.eventOrganizer eo " +
           "LEFT JOIN FETCH eo.user eu " +
           "WHERE eo.eoId = :eoId " +
           "ORDER BY s.startTime DESC")
    List<ShiftAssignment> findAllWithDetailsByEoId(@Param("eoId") UUID eoId);

    @Query("SELECT sa FROM ShiftAssignment sa " +
           "JOIN FETCH sa.worker w " +
           "LEFT JOIN FETCH w.user wu " +
           "JOIN FETCH sa.shift s " +
           "JOIN FETCH s.event e " +
           "JOIN FETCH e.eventOrganizer eo " +
           "LEFT JOIN FETCH eo.user eu " +
           "WHERE w.workerId = :workerId " +
           "ORDER BY s.startTime DESC")
    List<ShiftAssignment> findAllWithDetailsByWorkerId(@Param("workerId") UUID workerId);
}
