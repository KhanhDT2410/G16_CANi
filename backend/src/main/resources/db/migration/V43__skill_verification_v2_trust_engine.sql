-- =============================================================================
-- Flyway Migration: V43__skill_verification_v2_trust_engine.sql
-- Description: Nâng cấp bảng skill_verification_requests cho CANi Skill Trust &
--              Verification Engine V2 (TrustScore, Hard Gates, Audit Trail,
--              Idempotency Hash, Optimistic Locking & Fail-Closed Retry).
--              100% tương thích ngược, không xóa dữ liệu hiện có.
-- =============================================================================

ALTER TABLE skill_verification_requests
    ADD COLUMN IF NOT EXISTS certificate_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS expiry_date VARCHAR(50),
    ADD COLUMN IF NOT EXISTS document_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS model_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(30),
    ADD COLUMN IF NOT EXISTS ai_raw_response JSONB,
    ADD COLUMN IF NOT EXISTS trust_score NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS trust_score_breakdown JSONB,
    ADD COLUMN IF NOT EXISTS identity_verification_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS issuer_verification_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS document_authenticity_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS verification_source VARCHAR(100),
    ADD COLUMN IF NOT EXISTS verification_reference TEXT,
    ADD COLUMN IF NOT EXISTS human_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS failure_reason TEXT,
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_skill_verif_doc_hash
    ON skill_verification_requests(worker_id, document_hash);

CREATE INDEX IF NOT EXISTS idx_skill_verif_status_created
    ON skill_verification_requests(status, created_at);
