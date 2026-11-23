package com.cani.repository;

import com.cani.entity.WorkerGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkerGroupRepository extends JpaRepository<WorkerGroup, UUID> {

    List<WorkerGroup> findByLeaderWorkerId(UUID leaderWorkerId);

    List<WorkerGroup> findAllByIsActiveTrue();

    boolean existsByNameIgnoreCase(String name);

    Optional<WorkerGroup> findByInviteCodeIgnoreCase(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    @Query("SELECT wg FROM WorkerGroup wg WHERE " +
           "(:search IS NULL OR LOWER(wg.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           " LOWER(wg.skillFocus) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:isActive IS NULL OR wg.isActive = :isActive)")
    Page<WorkerGroup> searchWorkerGroups(
            @Param("search") String search,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}
