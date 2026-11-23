package com.cani.repository;

import com.cani.entity.SupportConversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportConversationRepository extends JpaRepository<SupportConversation, UUID> {
    Optional<SupportConversation> findFirstByUserIdAndDisputeIdIsNullOrderByCreatedAtAsc(UUID userId);
    List<SupportConversation> findByDisputeIdIsNullOrderByCreatedAtAsc();
    Optional<SupportConversation> findByUserId(UUID userId);
    Optional<SupportConversation> findByUserIdAndDisputeIdIsNull(UUID userId);
    List<SupportConversation> findByDisputeIdIsNull();
    List<SupportConversation> findByDisputeIdIsNullOrderByLastMessageAtDesc();
    List<SupportConversation> findAllByOrderByLastMessageAtDesc();
    List<SupportConversation> findByDisputeId(UUID disputeId);
    Optional<SupportConversation> findByDisputeIdAndDisputeParty(UUID disputeId, String disputeParty);
    List<SupportConversation> findByDisputeIdIsNotNull();
    List<SupportConversation> findByDisputeIdIsNotNullAndUnreadStaffCountGreaterThan(Integer minCount);
    List<SupportConversation> findByDisputeIdIsNotNullAndUserIdAndUnreadUserCountGreaterThan(UUID userId, Integer minCount);
    List<SupportConversation> findByDisputeIdIn(Collection<UUID> disputeIds);

    @Query("SELECT COALESCE(SUM(c.unreadStaffCount), 0) FROM SupportConversation c WHERE c.disputeId IS NULL")
    Integer sumUnreadStaffCount();

    @Query("SELECT COUNT(c) FROM SupportConversation c WHERE c.disputeId IS NULL AND c.unreadStaffCount > 0")
    int countUnreadStaffConversations();

    @Query("SELECT c FROM SupportConversation c WHERE c.disputeId IS NULL AND c.lastMessageAt IS NOT NULL ORDER BY c.lastMessageAt DESC")
    List<SupportConversation> findRecentSupportConversations(Pageable pageable);
}