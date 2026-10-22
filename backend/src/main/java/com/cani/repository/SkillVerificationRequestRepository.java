package com.cani.repository;

import com.cani.entity.SkillVerificationRequest;
import com.cani.entity.enums.SkillVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillVerificationRequestRepository extends JpaRepository<SkillVerificationRequest, UUID> {

    List<SkillVerificationRequest> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);

    List<SkillVerificationRequest> findByStatusOrderByCreatedAtAsc(SkillVerificationStatus status);
}
