package com.cani.repository;

import com.cani.entity.SupportTicket;
import com.cani.entity.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID>, JpaSpecificationExecutor<SupportTicket> {

    List<SupportTicket> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SupportTicket> findByStatusInOrderBySlaDeadlineAsc(Collection<TicketStatus> statuses);

    Optional<SupportTicket> findByReferenceNo(String referenceNo);

    List<SupportTicket> findByStatusInAndAssignedTo(Collection<TicketStatus> statuses, UUID assignedTo);

    long countByStatusInAndAssignedTo(Collection<TicketStatus> statuses, UUID assignedTo);

    long countByStatus(TicketStatus status);
}

