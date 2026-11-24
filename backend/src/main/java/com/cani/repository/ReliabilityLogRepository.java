package com.cani.repository;

import com.cani.entity.ReliabilityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReliabilityLogRepository extends JpaRepository<ReliabilityLog, UUID> {

    List<ReliabilityLog> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);

    @org.springframework.data.jpa.repository.Query("SELECT r FROM ReliabilityLog r WHERE r.worker.workerId = :workerId AND r.referenceType IN :refTypes AND r.createdAt >= :since ORDER BY r.createdAt DESC")
    List<ReliabilityLog> findViolationsByWorkerId(
            @org.springframework.data.repository.query.Param("workerId") UUID workerId,
            @org.springframework.data.repository.query.Param("refTypes") java.util.Collection<String> refTypes,
            @org.springframework.data.repository.query.Param("since") java.time.Instant since);
}
