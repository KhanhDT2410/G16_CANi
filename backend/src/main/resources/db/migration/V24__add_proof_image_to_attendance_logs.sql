-- Migration V23: Thêm cột lưu ảnh bằng chứng hoàn thành ca làm việc (Proof of Work)
ALTER TABLE attendance_logs ADD COLUMN IF NOT EXISTS proof_image_url VARCHAR(500);
ALTER TABLE attendance_logs ADD COLUMN IF NOT EXISTS proof_submitted_at TIMESTAMP WITH TIME ZONE;

