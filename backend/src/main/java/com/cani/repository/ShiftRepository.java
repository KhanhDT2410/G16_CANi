package com.cani.repository;

import com.cani.entity.Shift;
import com.cani.entity.enums.ShiftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shift s WHERE s.shiftId = :shiftId")
    Optional<Shift> findByIdForUpdate(@Param("shiftId") UUID shiftId);

    List<Shift> findByEvent_EventId(UUID eventId);
    List<Shift> findByEvent_EventIdAndStatus(UUID eventId, ShiftStatus status);
    List<Shift> findByEvent_EventIdAndStatusNot(UUID eventId, ShiftStatus status);
    List<Shift> findByStatus(ShiftStatus status);
    long countByStatus(ShiftStatus status);

    @Query("SELECT s.hourlyWage FROM Shift s WHERE LOWER(TRIM(s.jobType)) = LOWER(TRIM(:jobType)) AND s.status <> :cancelledStatus AND s.hourlyWage IS NOT NULL")
    List<java.math.BigDecimal> findHourlyWagesByJobTypeExcludingStatus(
            @Param("jobType") String jobType,
            @Param("cancelledStatus") ShiftStatus cancelledStatus
    );

    @Modifying
    @Query("UPDATE Shift s SET s.confirmedWorkers = s.confirmedWorkers + 1, s.remainingWorkers = CASE WHEN (s.remainingWorkers > 0) THEN s.remainingWorkers - 1 ELSE 0 END, s.status = CASE WHEN (s.confirmedWorkers + 1 >= s.requiredWorkers) THEN :fullStatus ELSE s.status END, s.emergency = CASE WHEN (s.confirmedWorkers + 1 >= s.requiredWorkers) THEN false ELSE s.emergency END, s.understaffedAlertSent = CASE WHEN (s.confirmedWorkers + 1 >= s.requiredWorkers) THEN false ELSE s.understaffedAlertSent END WHERE s.shiftId = :shiftId AND s.status = :activeStatus AND s.confirmedWorkers < s.requiredWorkers")
    int incrementConfirmedWorkersAtomic(@Param("shiftId") UUID shiftId, @Param("activeStatus") ShiftStatus activeStatus, @Param("fullStatus") ShiftStatus fullStatus);

    default int incrementConfirmedWorkersAtomic(UUID shiftId) {
        return incrementConfirmedWorkersAtomic(shiftId, ShiftStatus.ACTIVE, ShiftStatus.FULLY_STAFFED);
    }

    @Modifying
    @Query("UPDATE Shift s SET s.confirmedWorkers = CASE WHEN (s.confirmedWorkers > 0) THEN s.confirmedWorkers - 1 ELSE 0 END, s.remainingWorkers = s.remainingWorkers + 1, s.status = :activeStatus WHERE s.shiftId = :shiftId")
    int decrementConfirmedWorkersAtomic(@Param("shiftId") UUID shiftId, @Param("activeStatus") ShiftStatus activeStatus);

    default int decrementConfirmedWorkersAtomic(UUID shiftId) {
        return decrementConfirmedWorkersAtomic(shiftId, ShiftStatus.ACTIVE);
    }
}
