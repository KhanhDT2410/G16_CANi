package com.cani.repository;

import com.cani.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {
    List<SupportMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
    List<SupportMessage> findByConversationIdAndIsReadFalseAndSenderRole(UUID conversationId, String senderRole);
    Optional<SupportMessage> findFirstByConversationIdAndShiftIdIsNotNullOrderByCreatedAtDesc(UUID conversationId);
    List<SupportMessage> findByShiftIdOrderByCreatedAtAsc(UUID shiftId);
    List<SupportMessage> findByShiftIdInOrderByCreatedAtAsc(Collection<UUID> shiftIds);
}
