package com.cani.repository;

import com.cani.entity.Payment;
import com.cani.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByEventId(UUID eventId);
    List<Payment> findByShiftId(UUID shiftId);
    Optional<Payment> findByAssignmentId(UUID assignmentId);
    List<Payment> findByEventOrganizer_EoIdOrderByCreatedAtDesc(UUID eoId);
    List<Payment> findByWorker_WorkerIdOrderByCreatedAtDesc(UUID workerId);

    List<Payment> findByCreatedAtBetween(Instant from, Instant to);
    Page<Payment> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);
    Page<Payment> findByStatusAndCreatedAtBetween(PaymentStatus status, Instant from, Instant to, Pageable pageable);
    List<Payment> findByStatusInAndCreatedAtBetween(Collection<PaymentStatus> statuses, Instant from, Instant to);
    long countByCreatedAtBetween(Instant from, Instant to);
}

