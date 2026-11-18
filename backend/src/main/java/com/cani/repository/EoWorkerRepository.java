package com.cani.repository;

import com.cani.entity.EoWorker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EoWorkerRepository extends JpaRepository<EoWorker, UUID> {

    Optional<EoWorker> findByEventOrganizer_EoIdAndWorker_WorkerId(UUID eoId, UUID workerId);

    boolean existsByEventOrganizer_EoIdAndWorker_WorkerId(UUID eoId, UUID workerId);

    void deleteByEventOrganizer_EoIdAndWorker_WorkerId(UUID eoId, UUID workerId);

    @Query("SELECT ew FROM EoWorker ew " +
           "JOIN FETCH ew.worker w " +
           "JOIN FETCH w.user u " +
           "WHERE ew.eventOrganizer.eoId = :eoId " +
           "AND (:favoritedOnly IS NULL OR :favoritedOnly = false OR ew.isFavorited = true) " +
           "AND (:search IS NULL OR LOWER(w.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.phone) LIKE CONCAT('%', :search, '%')) " +
           "ORDER BY ew.isFavorited DESC, ew.createdAt DESC")
    List<EoWorker> findMyTeamMembers(
            @Param("eoId") UUID eoId,
            @Param("search") String search,
            @Param("favoritedOnly") Boolean favoritedOnly
    );
}
