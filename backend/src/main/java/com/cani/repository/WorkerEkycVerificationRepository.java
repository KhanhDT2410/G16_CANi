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

    List<WorkerEkycVerification> findByWorker_WorkerIdInOrderByCreatedAtDesc(Collection<UUID> workerIds);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"worker", "worker.user"})
    Optional<WorkerEkycVerification> findById(UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"worker", "worker.user"})
    @org.springframework.data.jpa.repository.Query("select v from WorkerEkycVerification v where v.id = :id")
    Optional<WorkerEkycVerification> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

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

    List<WorkerEkycVerification> findByImagesPurgedAtIsNullAndImageRetentionUntilBefore(Instant cutoff);

    long countByImagesPurgedAtIsNullAndImageRetentionUntilIsNull();

    @org.springframework.data.jpa.repository.Query("""
            select v.status, count(v) from WorkerEkycVerification v
            where v.imagesPurgedAt is null and v.imageRetentionUntil is null
            group by v.status
            """)
    List<Object[]> countLegacyNullRetentionByStatus();

    @org.springframework.data.jpa.repository.Query("""
            select v.id from WorkerEkycVerification v
            where v.status = 'PROCESSING'
              and coalesce(v.processingStartedAt, v.updatedAt, v.createdAt) < :cutoff
            """)
    List<UUID> findExpiredProcessingIds(@org.springframework.data.repository.query.Param("cutoff") Instant cutoff);

    long countByStatus(String status);
    boolean existsByWorker_WorkerIdAndStatus(UUID ownerId, String status);
    boolean existsByWorker_WorkerIdAndStatusIn(UUID workerId, Collection<String> statuses);
    boolean existsByWorker_WorkerIdAndLegalHoldTrue(UUID workerId);

    @org.springframework.data.jpa.repository.Query("""
            select (count(v) > 0) from WorkerEkycVerification v
            where v.frontImageUrl = :key or v.backImageUrl = :key or v.selfieImageUrl = :key
            """)
    boolean existsImageReference(String key);
}
