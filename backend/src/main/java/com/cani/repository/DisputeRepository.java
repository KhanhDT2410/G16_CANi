package com.cani.repository;

import com.cani.entity.Dispute;
import com.cani.entity.enums.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID>, JpaSpecificationExecutor<Dispute> {

    boolean existsByAssignmentIdAndStatusIn(UUID assignmentId, List<DisputeStatus> openStatuses);

    List<Dispute> findByComplainantIdOrderByCreatedAtDesc(UUID complainantId);

    List<Dispute> findByRespondentIdOrderByCreatedAtDesc(UUID respondentId);

    List<Dispute> findByComplainantIdOrRespondentIdOrderByCreatedAtDesc(UUID complainantId, UUID respondentId);

    List<Dispute> findByAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);

    List<Dispute> findByStatusIn(List<DisputeStatus> statuses);

    List<Dispute> findByStatusInAndAssignedTo(List<DisputeStatus> statuses, UUID assignedTo);

    List<Dispute> findByStatusInAndAssignedToIsNull(List<DisputeStatus> statuses);

    long countByStatus(DisputeStatus status);

    List<Dispute> findByStatusInAndSlaDeadlineBefore(List<DisputeStatus> statuses, java.time.Instant now);
}
