package com.cani.repository;

import com.cani.entity.ShiftAssignment;
import com.cani.entity.enums.AssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, UUID> {
    interface ShiftAssignmentCountProjection {
        UUID getShiftId();
        long getTotalCount();
        long getConfirmedCount();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.assignmentId = :assignmentId")
    Optional<ShiftAssignment> findByIdForUpdate(@Param("assignmentId") UUID assignmentId);

    @EntityGraph(attributePaths = {"worker", "worker.user", "shift"})
    List<ShiftAssignment> findByShift_ShiftId(UUID shiftId);

    @EntityGraph(attributePaths = {"worker", "worker.user", "shift"})
    List<ShiftAssignment> findByShift_ShiftIdIn(Collection<UUID> shiftIds);

    List<ShiftAssignment> findByGroupId(UUID groupId);

    @EntityGraph(attributePaths = {"worker", "worker.user", "shift", "shift.event", "shift.event.eventOrganizer", "shift.event.eventOrganizer.user"})
    List<ShiftAssignment> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);

    @EntityGraph(attributePaths = {"shift", "shift.event"})
    List<ShiftAssignment> findByAssignmentIdIn(Collection<UUID> assignmentIds);

    @EntityGraph(attributePaths = {"shift"})
    List<ShiftAssignment> findByWorker_WorkerIdAndStatusIn(UUID workerId, List<AssignmentStatus> statuses);

    Optional<ShiftAssignment> findByShift_ShiftIdAndWorker_WorkerId(UUID shiftId, UUID workerId);
    boolean existsByShift_ShiftId(UUID shiftId);
    boolean existsByWorker_WorkerId(UUID workerId);
    long countByShift_ShiftIdAndStatusIn(UUID shiftId, List<AssignmentStatus> statuses);
    boolean existsByShift_ShiftIdAndWorker_WorkerId(UUID shiftId, UUID workerId);
    boolean existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(UUID shiftId, UUID workerId, java.util.Collection<AssignmentStatus> statuses);

    @Query("SELECT sa.shift.shiftId FROM ShiftAssignment sa WHERE sa.worker.workerId = :workerId")
    List<UUID> findShiftIdsByWorkerId(@Param("workerId") UUID workerId);

    @Query("SELECT sa.shift.shiftId AS shiftId, " +
           "COUNT(sa) AS totalCount, " +
           "COUNT(CASE WHEN sa.status IN :validStatuses THEN sa.assignmentId END) AS confirmedCount " +
           "FROM ShiftAssignment sa " +
           "WHERE sa.shift.shiftId IN :shiftIds " +
           "GROUP BY sa.shift.shiftId")
    List<ShiftAssignmentCountProjection> countAssignmentsByShiftIds(
            @Param("shiftIds") Collection<UUID> shiftIds,
            @Param("validStatuses") Collection<AssignmentStatus> validStatuses
    );

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
