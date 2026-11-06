package com.cani.repository;

import com.cani.entity.Shift;
import com.cani.entity.enums.ShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, UUID> {
    List<Shift> findByEvent_EventId(UUID eventId);
    List<Shift> findByEvent_EventIdAndStatus(UUID eventId, ShiftStatus status);
    List<Shift> findByStatus(ShiftStatus status);
    long countByStatus(ShiftStatus status);

    @Modifying
    @Query("UPDATE Shift s SET s.confirmedWorkers = s.confirmedWorkers + 1, s.remainingWorkers = CASE WHEN (s.remainingWorkers > 0) THEN s.remainingWorkers - 1 ELSE 0 END, s.status = CASE WHEN (s.confirmedWorkers + 1 >= s.requiredWorkers) THEN com.cani.entity.enums.ShiftStatus.FULLY_STAFFED ELSE s.status END WHERE s.shiftId = :shiftId AND s.confirmedWorkers < s.requiredWorkers")
    int incrementConfirmedWorkersAtomic(@Param("shiftId") UUID shiftId);

    @Modifying
    @Query("UPDATE Shift s SET s.confirmedWorkers = CASE WHEN (s.confirmedWorkers > 0) THEN s.confirmedWorkers - 1 ELSE 0 END, s.remainingWorkers = s.remainingWorkers + 1, s.status = com.cani.entity.enums.ShiftStatus.ACTIVE WHERE s.shiftId = :shiftId")
    int decrementConfirmedWorkersAtomic(@Param("shiftId") UUID shiftId);
}
