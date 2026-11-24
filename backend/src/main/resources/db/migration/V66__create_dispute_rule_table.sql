-- V66: Bảng dispute_rule phục vụ quản lý quy tắc xử lý khiếu nại và chế tài
CREATE TABLE IF NOT EXISTS dispute_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    behavior TEXT NOT NULL,
    violating_party VARCHAR(20) NOT NULL,
    sanction_form TEXT NOT NULL,
    specific_penalty TEXT NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    fault_outcome VARCHAR(50) NOT NULL,
    repeat_window_days INT,
    repeat_index INT DEFAULT 1,
    sanction_text TEXT NOT NULL,
    admin_action_text TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_dispute_rule_type_enabled ON dispute_rule (rule_type, enabled);
CREATE INDEX IF NOT EXISTS idx_dispute_rule_code ON dispute_rule (code);
