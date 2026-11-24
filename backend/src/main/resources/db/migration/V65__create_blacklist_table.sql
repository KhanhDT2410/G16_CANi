-- V65: Bảng Blacklist lưu trữ định danh cấm (CCCD, SĐT) cho các vi phạm nghiêm trọng
CREATE TABLE IF NOT EXISTS blacklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier VARCHAR(100) NOT NULL,
    identifier_type VARCHAR(20) NOT NULL,
    permanent BOOLEAN NOT NULL DEFAULT false,
    reference_type VARCHAR(50),
    reference_id UUID,
    reason TEXT NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_blacklist_identifier_type UNIQUE (identifier, identifier_type)
);

CREATE INDEX IF NOT EXISTS idx_blacklist_identifier_type ON blacklist (identifier, identifier_type);
