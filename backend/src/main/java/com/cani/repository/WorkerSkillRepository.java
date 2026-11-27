package com.cani.repository;

import com.cani.entity.WorkerSkill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkerSkillRepository extends JpaRepository<WorkerSkill, UUID> {

    @EntityGraph(attributePaths = "skill")
    List<WorkerSkill> findByWorker_WorkerId(UUID workerId);

    @EntityGraph(attributePaths = "skill")
    List<WorkerSkill> findByWorker_WorkerIdIn(Collection<UUID> workerIds);

    Optional<WorkerSkill> findByWorker_WorkerIdAndSkill_SkillId(UUID workerId, UUID skillId);

    List<WorkerSkill> findByEvidenceRequestId(UUID evidenceRequestId);
}
