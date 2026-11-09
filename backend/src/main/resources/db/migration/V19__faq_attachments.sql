-- =============================================================================
-- V19: Bổ sung hình ảnh và tệp đính kèm cho câu hỏi thường gặp FAQ (ADM-08)
-- =============================================================================

ALTER TABLE faqs
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS attachment_url VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS attachment_name VARCHAR(255);

