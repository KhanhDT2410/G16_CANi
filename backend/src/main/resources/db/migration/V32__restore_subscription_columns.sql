-- =============================================================================
-- Migration V32: Bổ sung các cột thông tin gói cước còn lại cho bảng subscriptions
-- Đồng bộ tương thích hoàn toàn giữa schema PostgreSQL và JPA Entity Subscription
-- =============================================================================

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS plan_name VARCHAR(100);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS plan_type subscription_plan;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS price NUMERIC(15, 2) DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS features JSONB;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS max_events INT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS max_shifts INT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS max_workers INT;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS events_used INT DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS shifts_used INT DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'plans') THEN
        UPDATE subscriptions s
        SET plan_name = p.plan_name,
            plan_type = p.plan_type,
            price = COALESCE(p.price, 0),
            features = p.features,
            max_events = p.max_events,
            max_shifts = p.max_shifts,
            max_workers = p.max_workers
        FROM plans p
        WHERE s.plan_id = p.id;
    END IF;
END $$;
