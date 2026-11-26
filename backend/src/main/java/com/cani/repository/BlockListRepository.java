package com.cani.repository;

import com.cani.entity.BlockList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface BlockListRepository extends JpaRepository<BlockList, UUID> {

    @Query("SELECT b FROM BlockList b " +
           "JOIN FETCH b.blocked u " +
           "WHERE b.blocker.userId = :blockerId " +
           "ORDER BY b.createdAt DESC")
    List<BlockList> findByBlockerIdWithDetails(@Param("blockerId") UUID blockerId);

    boolean existsByBlocker_UserIdAndBlocked_UserId(UUID blockerId, UUID blockedId);

    Optional<BlockList> findByBlocker_UserIdAndBlocked_UserId(UUID blockerId, UUID blockedId);

    void deleteByBlocker_UserIdAndBlocked_UserId(UUID blockerId, UUID blockedId);

    @Query("SELECT CASE WHEN b.blocker.userId = :userId THEN b.blocked.userId ELSE b.blocker.userId END " +
           "FROM BlockList b WHERE b.blocker.userId = :userId OR b.blocked.userId = :userId")
    Set<UUID> findMutuallyBlockedUserIds(@Param("userId") UUID userId);
}
