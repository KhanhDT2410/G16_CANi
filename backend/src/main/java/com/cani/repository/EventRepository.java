package com.cani.repository;

import com.cani.entity.Event;
import com.cani.entity.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByEventOrganizer_EoIdOrderByCreatedAtDesc(UUID eoId);
    List<Event> findByStatus(EventStatus status);
    Optional<Event> findByEventIdAndEventOrganizer_EoId(UUID eventId, UUID eoId);
}
