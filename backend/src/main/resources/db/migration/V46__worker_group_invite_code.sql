-- V46: Add invite_code to worker_groups for self-service squad joining
ALTER TABLE worker_groups ADD COLUMN IF NOT EXISTS invite_code VARCHAR(12) NULL;

-- Unique index on non-null invite_code
CREATE UNIQUE INDEX IF NOT EXISTS idx_worker_groups_invite_code ON worker_groups (invite_code) WHERE invite_code IS NOT NULL;
