package com.cani.repository;

import com.cani.entity.SupportTicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, UUID> {

    List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    Optional<SupportTicketMessage> findFirstByTicketIdOrderByCreatedAtDesc(UUID ticketId);

    long countByTicketId(UUID ticketId);
}

