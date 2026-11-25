-- =============================================================================
-- Migration V72: Chuẩn hóa Toàn vẹn Khóa ngoại & Triệt tiêu Vòng lặp Khóa ngoại
-- 1. Triệt tiêu vòng lặp khóa ngoại (Circular Dependency) giữa event_organizers và subscriptions
-- 2. Bổ sung ràng buộc khóa ngoại (FK Invariants) cho payos_orders.eo_id -> event_organizers(id)
-- 3. Bổ sung cột & khóa ngoại payos_orders.plan_id -> plans(id)
-- 4. Bổ sung ràng buộc khóa ngoại ekyc_consent_records.user_id -> users(id)
-- 5. Bổ sung chỉ mục hiệu năng cao idx_subscriptions_eo_status_expires
-- =============================================================================

-- -----------------------------------------------------------------------------
-- PHẦN 1: TRIỆT TIÊU VÒNG LẶP KHÓA NGOẠI (CIRCULAR DEPENDENCY)
-- Bản chất: Quan hệ giữa event_organizers và subscriptions là 1-N (One-to-Many).
-- Cột event_organizers.subscription_id là con trỏ phi chuẩn hóa gây ra vòng lặp
-- phụ thuộc khóa ngoại giữa 2 bảng.
-- Giải pháp: Gỡ bỏ khóa ngoại vòng fk_eo_subscription và xóa cột subscription_id.
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_eo_subscription' AND table_name = 'event_organizers'
    ) THEN
        ALTER TABLE event_organizers DROP CONSTRAINT fk_eo_subscription;
    END IF;
END $$;

ALTER TABLE event_organizers DROP COLUMN IF EXISTS subscription_id;

-- Bổ sung chỉ mục tổ hợp tối ưu hóa truy vấn gói active của EO
CREATE INDEX IF NOT EXISTS idx_subscriptions_eo_status_expires
    ON subscriptions(organizer_id, status, expires_at DESC);

-- -----------------------------------------------------------------------------
-- PHẦN 2: BỔ SUNG RÀNG BUỘC KHÓA NGOẠI CHO payos_orders
-- -----------------------------------------------------------------------------
-- 2.1. Dọn dẹp dữ liệu rác/mồ côi trước khi gắn constraint (nếu có)
DELETE FROM payos_orders WHERE eo_id NOT IN (SELECT id FROM event_organizers);

-- 2.2. Khóa ngoại payos_orders.eo_id -> event_organizers(id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_payos_orders_eo' AND table_name = 'payos_orders'
    ) THEN
        ALTER TABLE payos_orders
            ADD CONSTRAINT fk_payos_orders_eo
            FOREIGN KEY (eo_id) REFERENCES event_organizers(id) ON DELETE CASCADE;
    END IF;
END $$;

-- 2.3. Bổ sung cột plan_id và khóa ngoại payos_orders.plan_id -> plans(id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'payos_orders' AND column_name = 'plan_id'
    ) THEN
        ALTER TABLE payos_orders ADD COLUMN plan_id UUID;
    END IF;
END $$;

-- Điền ngược (backfill) plan_id từ bảng plans dựa trên plan_type
UPDATE payos_orders po
SET plan_id = p.id
FROM plans p
WHERE po.plan_type = p.plan_type::text AND po.plan_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_payos_orders_plan' AND table_name = 'payos_orders'
    ) THEN
        ALTER TABLE payos_orders
            ADD CONSTRAINT fk_payos_orders_plan
            FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_payos_orders_plan_id ON payos_orders(plan_id);

-- -----------------------------------------------------------------------------
-- PHẦN 3: BỔ SUNG RÀNG BUỘC KHÓA NGOẠI CHO ekyc_consent_records
-- -----------------------------------------------------------------------------
-- 3.1. Dọn dẹp bản ghi không hợp lệ trước khi gắn constraint (nếu có)
DELETE FROM ekyc_consent_records WHERE user_id NOT IN (SELECT id FROM users);

-- 3.2. Khóa ngoại ekyc_consent_records.user_id -> users(id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_ekyc_consent_user' AND table_name = 'ekyc_consent_records'
    ) THEN
        ALTER TABLE ekyc_consent_records
            ADD CONSTRAINT fk_ekyc_consent_user
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;
