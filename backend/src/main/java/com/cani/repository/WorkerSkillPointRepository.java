package com.cani.repository;

import com.cani.entity.WorkerSkillPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkerSkillPointRepository extends JpaRepository<WorkerSkillPoint, UUID> {

    Optional<WorkerSkillPoint> findByWorker_WorkerIdAndJobType(UUID workerId, String jobType);

    List<WorkerSkillPoint> findByWorker_WorkerId(UUID workerId);
}
