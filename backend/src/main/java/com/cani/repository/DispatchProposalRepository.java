package com.cani.repository;

import com.cani.entity.DispatchProposal;
import com.cani.entity.enums.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispatchProposalRepository extends JpaRepository<DispatchProposal, UUID> {
    List<DispatchProposal> findByShift_ShiftId(UUID shiftId);
    List<DispatchProposal> findByWorker_WorkerIdAndStatusOrderByCreatedAtDesc(UUID workerId, ProposalStatus status);
    List<DispatchProposal> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);
    Optional<DispatchProposal> findByShift_ShiftIdAndWorker_WorkerId(UUID shiftId, UUID workerId);
    boolean existsByShift_ShiftIdAndWorker_WorkerId(UUID shiftId, UUID workerId);
    boolean existsByShift_ShiftIdAndWorker_WorkerIdAndStatusIn(UUID shiftId, UUID workerId, List<ProposalStatus> statuses);
    List<DispatchProposal> findByStatusAndExpiresAtBefore(ProposalStatus status, Instant expiresAt);
    List<DispatchProposal> findByStatus(ProposalStatus status);
    List<DispatchProposal> findByShift_ShiftIdAndGroupIdAndStatusIn(UUID shiftId, UUID groupId, List<ProposalStatus> statuses);
    List<DispatchProposal> findByGroupIdAndStatus(UUID groupId, ProposalStatus status);
}
