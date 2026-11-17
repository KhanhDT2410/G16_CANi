package com.cani.repository;

import com.cani.entity.EventOrganizer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventOrganizerRepository extends JpaRepository<EventOrganizer, UUID> {

    Optional<EventOrganizer> findByUser_UserId(UUID userId);

    List<EventOrganizer> findByUser_UserIdIn(Collection<UUID> userIds);
}
