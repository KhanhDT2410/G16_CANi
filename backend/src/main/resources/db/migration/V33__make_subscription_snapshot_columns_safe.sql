-- =============================================================================
-- Migration V33: Đảm bảo tương thích hoàn hảo giữa snapshot gói cước và bảng subscriptions
-- Loại bỏ triệt để rủi ro NOT NULL constraint cho các cột snapshot của bảng subscriptions
-- =============================================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'subscriptions' AND column_name = 'plan_name'
    ) THEN
        ALTER TABLE subscriptions ALTER COLUMN plan_name DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'subscriptions' AND column_name = 'plan_type'
    ) THEN
        ALTER TABLE subscriptions ALTER COLUMN plan_type DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'subscriptions' AND column_name = 'price'
    ) THEN
        ALTER TABLE subscriptions ALTER COLUMN price DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'subscriptions' AND column_name = 'duration_days'
    ) THEN
        ALTER TABLE subscriptions ALTER COLUMN duration_days DROP NOT NULL;
    END IF;
END $$;

-- Đồng bộ dữ liệu lịch sử cho các subscriptions cũ nếu bị NULL snapshot
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'plans') THEN
        UPDATE subscriptions s
        SET plan_name = COALESCE(s.plan_name, p.plan_name),
            plan_type = COALESCE(s.plan_type, p.plan_type),
            price = COALESCE(s.price, s.price_paid, p.price, 0),
            duration_days = COALESCE(s.duration_days, p.duration_days, 30),
            max_events = COALESCE(s.max_events, p.max_events),
            max_shifts = COALESCE(s.max_shifts, p.max_shifts),
            max_workers = COALESCE(s.max_workers, p.max_workers),
            features = COALESCE(s.features, p.features)
        FROM plans p
        WHERE s.plan_id = p.id;
    END IF;
END $$;
