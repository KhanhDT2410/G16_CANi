package com.cani.repository;

import com.cani.entity.WorkerEkycVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkerEkycVerificationRepository extends JpaRepository<WorkerEkycVerification, UUID> {

    List<WorkerEkycVerification> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);

    Optional<WorkerEkycVerification> findTopByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);

    List<WorkerEkycVerification> findByStatusOrderByCreatedAtDesc(String status);

    boolean existsByCccdNumberHashAndStatus(String cccdNumberHash, String status);

    boolean existsByCccdNumberHashAndStatusAndWorker_WorkerIdNot(String cccdNumberHash, String status, UUID workerId);

    long countByStatus(String status);
}
