package com.cani.repository;

import com.cani.entity.WorkerEkycVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkerEkycVerificationRepository extends JpaRepository<WorkerEkycVerification, UUID> {

    List<WorkerEkycVerification> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"worker", "worker.user"})
    Optional<WorkerEkycVerification> findById(UUID id);

    Optional<WorkerEkycVerification> findTopByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);

    Optional<WorkerEkycVerification> findTopByWorker_WorkerIdAndStatusInOrderByCreatedAtDesc(
            UUID workerId, Collection<String> statuses);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "worker")
    List<WorkerEkycVerification> findByStatusOrderByCreatedAtDesc(String status);

    boolean existsByCccdNumberHashAndStatus(String cccdNumberHash, String status);

    boolean existsByCccdNumberHashAndStatusAndWorker_WorkerIdNot(String cccdNumberHash, String status, UUID workerId);

    boolean existsByCccdNumberHashAndStatusInAndWorker_WorkerIdNot(
            String cccdNumberHash, Collection<String> statuses, UUID workerId);

    boolean existsByCccdNumberHashInAndStatusInAndWorker_WorkerIdNot(
            Collection<String> cccdNumberHashes, Collection<String> statuses, UUID workerId);

    boolean existsByCccdNumberHashAndStatusInAndWorker_User_UserIdNot(
            String cccdNumberHash, Collection<String> statuses, UUID userId);

    boolean existsByCccdNumberHashInAndStatusInAndWorker_User_UserIdNot(
            Collection<String> cccdNumberHashes, Collection<String> statuses, UUID userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<WorkerEkycVerification> findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(Instant cutoff);

    long countByStatus(String status);
}
