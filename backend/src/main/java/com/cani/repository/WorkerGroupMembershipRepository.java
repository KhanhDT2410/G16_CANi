package com.cani.repository;

import com.cani.entity.WorkerGroupMembership;
import com.cani.entity.enums.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkerGroupMembershipRepository extends JpaRepository<WorkerGroupMembership, UUID> {

    @Query("SELECT m FROM WorkerGroupMembership m " +
           "JOIN FETCH m.worker w " +
           "JOIN FETCH w.user u " +
           "WHERE m.workerGroup.groupId = :groupId " +
           "ORDER BY m.roleInGroup ASC, m.joinedAt ASC")
    List<WorkerGroupMembership> findByGroupIdWithWorkerDetails(@Param("groupId") UUID groupId);

    List<WorkerGroupMembership> findByWorkerGroup_GroupIdAndStatus(UUID groupId, MembershipStatus status);

    List<WorkerGroupMembership> findByWorker_WorkerIdAndStatus(UUID workerId, MembershipStatus status);

    Optional<WorkerGroupMembership> findByWorkerGroup_GroupIdAndWorker_WorkerId(UUID groupId, UUID workerId);

    boolean existsByWorkerGroup_GroupIdAndWorker_WorkerId(UUID groupId, UUID workerId);

    int countByWorkerGroup_GroupIdAndStatus(UUID groupId, MembershipStatus status);
}
