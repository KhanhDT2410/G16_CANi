package com.cani.repository;

import com.cani.entity.ReliabilityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReliabilityLogRepository extends JpaRepository<ReliabilityLog, UUID> {

    List<ReliabilityLog> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);
}
