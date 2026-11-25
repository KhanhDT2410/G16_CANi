package com.cani.repository;

import com.cani.entity.Event;
import com.cani.entity.enums.EventMode;
import com.cani.entity.enums.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByEventOrganizer_EoIdOrderByCreatedAtDesc(UUID eoId);
    List<Event> findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID eoId);
    Page<Event> findByEventOrganizer_EoIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID eoId, Pageable pageable);
    List<Event> findByStatus(EventStatus status);
    List<Event> findByStatusNotInAndDeletedAtIsNull(Collection<EventStatus> excludedStatuses);
    Optional<Event> findByEventIdAndEventOrganizer_EoId(UUID eventId, UUID eoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.eventId = :eventId AND e.eventOrganizer.eoId = :eoId")
    Optional<Event> findByEventIdAndEventOrganizer_EoIdForUpdate(@Param("eventId") UUID eventId, @Param("eoId") UUID eoId);

    long countByStatus(EventStatus status);

    List<Event> findByCreatedAtBetween(Instant from, Instant to);
    long countByModeAndCreatedAtBetween(EventMode mode, Instant from, Instant to);
}
