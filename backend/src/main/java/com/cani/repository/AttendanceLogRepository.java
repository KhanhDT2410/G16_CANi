package com.cani.repository;

import com.cani.entity.AttendanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, UUID> {
    Optional<AttendanceLog> findByShiftAssignment_AssignmentId(UUID assignmentId);
    List<AttendanceLog> findByWorker_WorkerId(UUID workerId);
}
