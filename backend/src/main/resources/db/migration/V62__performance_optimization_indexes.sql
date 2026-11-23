-- =============================================================================
-- Migration V62: Performance Optimization Indexes
-- Tối ưu truy vấn cho ShiftLifecycleService, EoEventQueryService,
-- ShiftAssignment/DispatchProposal batch count và DirectChatService.
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_shifts_status_end_time
    ON shifts (status, end_time);

CREATE INDEX IF NOT EXISTS idx_shifts_event_id_status
    ON shifts (event_id, status);

CREATE INDEX IF NOT EXISTS idx_events_eo_active_created
    ON events (eo_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_shift_assignments_shift_status
    ON shift_assignments (shift_id, status);

CREATE INDEX IF NOT EXISTS idx_dispatch_proposals_shift_status
    ON dispatch_proposals (shift_id, status);

CREATE INDEX IF NOT EXISTS idx_chat_participants_user_unread
    ON chat_participants (user_id, unread_count)
    WHERE unread_count > 0;
