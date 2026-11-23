package com.cani.repository;

import com.cani.entity.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, UUID> {

    Optional<ChatParticipant> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    List<ChatParticipant> findByUserId(UUID userId);

    List<ChatParticipant> findByConversationId(UUID conversationId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    @Query("SELECT p1.conversationId FROM ChatParticipant p1 " +
           "JOIN ChatParticipant p2 ON p1.conversationId = p2.conversationId " +
           "JOIN ChatConversation c ON c.id = p1.conversationId " +
           "WHERE c.type = 'DIRECT' AND p1.userId = :user1 AND p2.userId = :user2")
    List<UUID> findDirectConversationIds(@Param("user1") UUID user1, @Param("user2") UUID user2);

    @Query("SELECT COUNT(p) FROM ChatParticipant p WHERE p.userId = :userId AND p.unreadCount > 0")
    long countUnreadConversationsByUserId(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(SUM(p.unreadCount), 0) FROM ChatParticipant p WHERE p.userId = :userId AND p.unreadCount > 0")
    long sumUnreadMessagesByUserId(@Param("userId") UUID userId);
}
