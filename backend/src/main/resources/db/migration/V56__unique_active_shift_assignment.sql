-- V56: Prevent duplicate active shift assignments for the same worker in the same shift.
-- Active statuses in AssignmentStatus enum: PENDING, CONFIRMED, CHECKED_IN.
-- Inactive statuses (exempt from unique constraint): COMPLETED, CANCELLED, NO_SHOW.
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_shift_worker_assignment
    ON shift_assignments (shift_id, worker_id)
    WHERE status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN');
