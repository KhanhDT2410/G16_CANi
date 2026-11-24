-- V64: Bảng Strike cho Event Organizer khi có phán quyết vi phạm tranh chấp (EO_FAULT)
CREATE TABLE IF NOT EXISTS eo_strike (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    eo_id UUID NOT NULL REFERENCES event_organizers(id) ON DELETE CASCADE,
    dispute_id UUID NOT NULL REFERENCES disputes(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_eo_strike_dispute UNIQUE (dispute_id)
);

CREATE INDEX IF NOT EXISTS idx_eo_strike_eo_id_created_at ON eo_strike (eo_id, created_at);
