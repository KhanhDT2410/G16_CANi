package com.cani.repository;

import com.cani.entity.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByCode(String code);

    Optional<Invite> findByCodeAndIsActiveTrue(String code);

    Optional<Invite> findFirstByShift_ShiftIdAndIsActiveTrueOrderByCreatedAtDesc(UUID shiftId);

    List<Invite> findByEvent_EventIdOrderByCreatedAtDesc(UUID eventId);
}

