package com.cani.repository;

import com.cani.entity.SupportConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportConversationRepository extends JpaRepository<SupportConversation, UUID> {
    Optional<SupportConversation> findByUserId(UUID userId);
    List<SupportConversation> findAllByOrderByLastMessageAtDesc();
}
