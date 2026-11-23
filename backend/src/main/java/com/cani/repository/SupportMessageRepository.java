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

    @org.springframework.data.jpa.repository.Query("SELECT m FROM SupportMessage m WHERE m.shiftId IS NOT NULL AND m.conversationId IN :convIds ORDER BY m.createdAt DESC")
    List<SupportMessage> findShiftMessagesForConversations(@org.springframework.data.repository.query.Param("convIds") Collection<UUID> convIds);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM SupportMessage m WHERE m.conversationId IN :conversationIds")
    int deleteByConversationIdIn(@org.springframework.data.repository.query.Param("conversationIds") Collection<UUID> conversationIds);
}
