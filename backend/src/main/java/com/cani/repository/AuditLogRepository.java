package com.cani.repository;

import com.cani.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findTop10ByOrderByCreatedAtDesc();

    Optional<AuditLog> findTopByEntityTypeAndEntityIdAndActionOrderByCreatedAtDesc(String entityType, UUID entityId, String action);

    @org.springframework.data.jpa.repository.Query("SELECT a FROM AuditLog a WHERE (a.entityId = :userId OR a.userId = :userId) AND a.action LIKE %:actionPattern% AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditLog> findViolationsByUserId(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("actionPattern") String actionPattern,
            @org.springframework.data.repository.query.Param("since") java.time.Instant since);
}
