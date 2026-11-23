-- =============================================================================
-- Migration V51: Theo doi trang thai da gui canh bao thieu nhan su sat gio theo dot
-- =============================================================================
ALTER TABLE shifts
    ADD COLUMN IF NOT EXISTS understaffed_alert_sent BOOLEAN NOT NULL DEFAULT FALSE;
