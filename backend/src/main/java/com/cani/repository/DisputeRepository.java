package com.cani.repository;

import com.cani.entity.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    List<Dispute> findByComplainantIdOrRespondentIdOrderByCreatedAtDesc(UUID complainantId, UUID respondentId);

    List<Dispute> findByAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);
}

