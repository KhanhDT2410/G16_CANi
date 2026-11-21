package com.cani.repository;

import com.cani.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    Optional<Worker> findByUser_UserId(UUID userId);

    List<Worker> findByUser_UserIdIn(Collection<UUID> userIds);

    List<Worker> findByIsOnlineTrue();

    @Query("SELECT w FROM Worker w JOIN FETCH w.user u " +
           "WHERE LOWER(w.fullName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR COALESCE(u.phone, '') LIKE CONCAT('%', :q, '%') " +
           "OR COALESCE(u.phone, '') LIKE CONCAT('%', :altPhone, '%') " +
           "ORDER BY w.ratingAvg DESC, w.totalShiftsCompleted DESC")
    List<Worker> searchWorkersByKeyword(
            @Param("q") String keyword,
            @Param("altPhone") String altPhoneKeyword
    );

    boolean existsByCccdNumberInAndWorkerIdNot(Collection<String> cccdNumbers, UUID workerId);

    boolean existsByCccdNumberIn(Collection<String> cccdNumbers);
}
