-- =============================================================================
-- Flyway Migration: V7__wallet_and_dispatch_enhancement.sql
-- Description: Bổ sung Index tối ưu hóa cho điều phối Matching/Dispatch 
--              và quản lý thanh toán trực tiếp (Direct Payment & Declaration).
--              Tuân thủ mô hình Non-Escrow (Không ví nội bộ, không giữ cọc).
-- =============================================================================

-- 1. Index cho tìm kiếm Shift theo trạng thái và thời gian
CREATE INDEX IF NOT EXISTS idx_shifts_status_time ON shifts(status, start_time);

-- 2. Index cho Dispatch Proposals
CREATE INDEX IF NOT EXISTS idx_dispatch_proposals_shift_worker ON dispatch_proposals(shift_id, worker_id, status);

-- 3. Index cho Shift Assignments phục vụ kiểm tra trùng lịch (Overlap Check)
CREATE INDEX IF NOT EXISTS idx_shift_assignments_worker_status ON shift_assignments(worker_id, status);

-- 4. Index cho Payments theo Assignment và Trạng thái xác nhận
CREATE INDEX IF NOT EXISTS idx_payments_assignment_status ON payments(assignment_id, status);
