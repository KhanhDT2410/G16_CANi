-- V28: Đồng bộ danh mục gói dịch vụ vào bảng plans và kiểm tra cấu trúc bảng subscriptions

-- 1. Đảm bảo bảng plans tồn tại và đầy đủ cấu trúc
CREATE TABLE IF NOT EXISTS plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_name VARCHAR(100) NOT NULL UNIQUE,
    plan_type subscription_plan NOT NULL,
    price NUMERIC(15, 2) NOT NULL,
    duration_days INT NOT NULL,
    features JSONB,
    max_events INT,
    max_shifts INT,
    max_workers INT,
    base_matching_quota INT DEFAULT 0
);

-- 2. Đảm bảo bảng subscriptions có khóa ngoại plan_id và cột price_paid
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'subscriptions' AND column_name = 'plan_id'
    ) THEN
        ALTER TABLE subscriptions ADD COLUMN plan_id UUID REFERENCES plans(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' AND table_name = 'subscriptions' AND column_name = 'price_paid'
    ) THEN
        ALTER TABLE subscriptions ADD COLUMN price_paid NUMERIC(15, 2);
    END IF;
END $$;

-- 3. Đồng bộ 3 gói cước chuẩn do Admin quy định
INSERT INTO plans (id, plan_name, plan_type, price, duration_days, max_events, max_shifts, max_workers, base_matching_quota, features)
VALUES 
('7e847d77-27d4-4bf9-be89-8645b5067565', 'Gói Trải Nghiệm (Khởi Nghiệp)', 'FREE_TRIAL', 0.00, 30, 2, 6, 20, 2, 
 '["Miễn phí 2 Sự kiện đầu tiên", "Tối đa 6 Ca làm việc & 20 Nhân sự", "Kích hoạt Thuật toán Ghép đôi (Matching Engine)", "Hiển thị ca làm việc ưu tiên trên Bản đồ Job Radar di động", "Bắn thông báo đẩy (FCM Push) đến ứng viên phù hợp", "Tự động kích hoạt hỗ trợ khi có biến động ca làm"]'::jsonb)
ON CONFLICT (plan_name) DO UPDATE SET 
    features = EXCLUDED.features,
    max_events = EXCLUDED.max_events,
    max_shifts = EXCLUDED.max_shifts,
    max_workers = EXCLUDED.max_workers,
    base_matching_quota = EXCLUDED.base_matching_quota;

INSERT INTO plans (plan_name, plan_type, price, duration_days, max_events, max_shifts, max_workers, base_matching_quota, features)
VALUES 
('Gói Tiêu Chuẩn (Standard)', 'MATCHING_PLUS', 99000.00, 30, 5, 25, 80, 5, 
 '["Tạo 5 Sự kiện với tối đa 25 Ca làm việc & 80 Nhân sự", "Kích hoạt Thuật toán Ghép đôi (Matching Engine)", "Hiển thị ca làm việc ưu tiên trên Bản đồ Job Radar di động", "Bắn thông báo đẩy (FCM Push) đến ứng viên phù hợp", "Tự động kích hoạt hỗ trợ khi có biến động ca làm"]'::jsonb),
('Gói Chuyên Nghiệp (Pro / Doanh Nghiệp)', 'ENTERPRISE', 249000.00, 30, 20, 100, 300, 20, 
 '["Tạo 20 Sự kiện với 100 Ca làm việc & 300 Nhân sự", "Ưu tiên xếp hạng cao nhất trên Radar tuyển dụng", "Mở khóa tính năng Gọi Nhân Sự Khẩn Cấp (Emergency Dispatch)", "Báo cáo số liệu chuyên sâu & đối soát đa chiều", "Kênh chăm sóc & hỗ trợ kỹ thuật riêng 24/7"]'::jsonb)
ON CONFLICT (plan_name) DO UPDATE SET 
    price = EXCLUDED.price,
    duration_days = EXCLUDED.duration_days,
    max_events = EXCLUDED.max_events,
    max_shifts = EXCLUDED.max_shifts,
    max_workers = EXCLUDED.max_workers,
    features = EXCLUDED.features;
