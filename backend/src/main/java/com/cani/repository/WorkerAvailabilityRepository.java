package com.cani.repository;

import com.cani.entity.WorkerAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkerAvailabilityRepository extends JpaRepository<WorkerAvailability, UUID> {

    List<WorkerAvailability> findByWorker_WorkerIdOrderByDayOfWeekAscStartTimeAsc(UUID workerId);

    @Modifying
    @Query("DELETE FROM WorkerAvailability wa WHERE wa.worker.workerId = :workerId")
    void deleteByWorker_WorkerId(@Param("workerId") UUID workerId);
}
