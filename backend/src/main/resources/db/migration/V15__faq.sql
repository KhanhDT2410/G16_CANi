-- =============================================================================
-- V15: Quản lý Câu hỏi thường gặp FAQ & Help Center (SRS FE-12, UC-110)
-- - Bảng faqs: lưu thông tin câu hỏi, câu trả lời, phân loại danh mục, trạng thái và lượt xem
-- =============================================================================

CREATE TYPE faq_category AS ENUM ('ACCOUNT', 'SHIFT', 'PAYMENT', 'DISPUTE');
CREATE TYPE faq_status AS ENUM ('DRAFT', 'PUBLISHED', 'ARCHIVED');

CREATE TABLE faqs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category faq_category NOT NULL,
    question VARCHAR(500) NOT NULL,
    answer TEXT NOT NULL,
    status faq_status NOT NULL DEFAULT 'DRAFT',
    author_id UUID REFERENCES users(id),
    published_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    view_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_faqs_category ON faqs(category);
CREATE INDEX idx_faqs_status ON faqs(status);

CREATE TRIGGER trg_faqs_updated_at BEFORE UPDATE ON faqs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- RLS deny-all dự phòng (đồng bộ convention V6/V13)
ALTER TABLE faqs ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON faqs;
DROP POLICY IF EXISTS "deny_authenticated" ON faqs;
CREATE POLICY "deny_anon" ON faqs FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON faqs FOR ALL TO authenticated USING (false);

GRANT ALL ON faqs TO postgres;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON faqs TO service_role;
    END IF;
END $$;

