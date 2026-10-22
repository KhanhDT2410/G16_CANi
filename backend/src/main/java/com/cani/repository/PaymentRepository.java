package com.cani.repository;

import com.cani.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
