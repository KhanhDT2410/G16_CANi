package com.cani.repository;

import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.enums.SkillVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillVerificationRequestRepository extends JpaRepository<SkillVerificationRequest, UUID> {

    List<SkillVerificationRequest> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);

    List<SkillVerificationRequest> findByWorker_WorkerIdIn(Collection<UUID> workerIds);

    List<SkillVerificationRequest> findByStatusOrderByCreatedAtAsc(SkillVerificationStatus status);

    List<SkillVerificationRequest> findAllByOrderByCreatedAtDesc();

    List<SkillVerificationRequest> findByStatusOrderByCreatedAtDesc(SkillVerificationStatus status);

    long countByStatus(SkillVerificationStatus status);

    boolean existsByWorker_WorkerIdAndDocumentHashAndStatusIn(
            UUID workerId,
            String documentHash,
            Collection<SkillVerificationStatus> statuses
    );

    boolean existsByWorker_WorkerIdAndDocumentUrl(UUID workerId, String documentUrl);

    Optional<SkillVerificationRequest> findFirstByDocumentUrl(String documentUrl);
}
