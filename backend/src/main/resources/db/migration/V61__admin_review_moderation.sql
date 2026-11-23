-- ========================================================
-- V61: Admin Review Moderation & Anti-Spam Extensions
-- ========================================================

ALTER TABLE reviews 
    ADD COLUMN IF NOT EXISTS is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS hidden_reason VARCHAR(255),
    ADD COLUMN IF NOT EXISTS hidden_by_admin_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS hidden_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS is_flagged BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS flag_reason VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_reviews_reviewee_hidden ON reviews(reviewee_id, is_hidden);
CREATE INDEX IF NOT EXISTS idx_reviews_created_hidden ON reviews(created_at DESC, is_hidden);
CREATE INDEX IF NOT EXISTS idx_reviews_is_flagged ON reviews(is_flagged) WHERE is_flagged = TRUE;
