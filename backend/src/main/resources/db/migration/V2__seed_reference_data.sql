-- =============================================================================
-- Flyway Migration: V2__seed_reference_data.sql
-- Description: Dữ liệu tham chiếu ban đầu (Skills, Wage Matrix mẫu, System Config)
-- =============================================================================

-- 1. Seed Skills mẫu
INSERT INTO skills (name, category, description, is_active) VALUES
('Phục vụ bàn', 'F&B', 'Phục vụ món ăn và thức uống tại bàn tiệc', true),
('Phụ bếp', 'F&B', 'Sơ chế nguyên liệu và hỗ trợ đầu bếp', true),
('Pha chế (Bartender/Barista)', 'F&B', 'Pha chế đồ uống, cocktail, cà phê', true),
('Thu ngân', 'Operations', 'Vận hành máy POS và thanh toán', true),
('Lễ tân / Check-in', 'Guest Services', 'Đón khách, phát thẻ và hướng dẫn chỗ ngồi', true),
('Điều phối sự kiện', 'Operations', 'Hỗ trợ quản lý timeline và điều phối nhân sự', true),
('Kỹ thuật âm thanh - ánh sáng', 'Technical', 'Vận hành thiết bị AV cơ bản cho sự kiện', true),
('Bảo vệ / An ninh', 'Security', 'Kiểm soát lối ra vào và duy trì trật tự sự kiện', true),
('Hậu cần / Setup sự kiện', 'Logistics', 'Khuân vác, lắp đặt bàn ghế, backdrop, dọn dẹp', true),
('PG / PB', 'Marketing', 'Quảng bá sản phẩm, chụp hình và tương tác với khách', true)
ON CONFLICT (name) DO NOTHING;

-- 2. Seed System Config mẫu
INSERT INTO system_config (key, value, description) VALUES
('app_version', '1.0.0', 'Phiên bản hiện tại của ứng dụng'),
('commission_rate', '0.00', 'Tỷ lệ hoa hồng nền tảng (0% cho giai đoạn MVP)'),
('support_email', 'hotro@cani.vn', 'Email tiếp nhận khiếu nại và hỗ trợ'),
('min_registration_age', '18', 'Độ tuổi tối thiểu đăng ký làm nhân sự sự kiện')
ON CONFLICT (key) DO NOTHING;

-- 3. Seed Wage Matrix mẫu (3 thành phố × 5 time_slots × 3 lead_times cho job_type 'Phục vụ bàn')
DO $$
DECLARE
    c city;
    ts time_slot;
    lt lead_time;
    base_wage NUMERIC(12, 2);
    city_mult NUMERIC(4, 2);
    slot_mult NUMERIC(4, 2);
    lead_mult NUMERIC(4, 2);
BEGIN
    FOR c IN SELECT unnest(enum_range(NULL::city)) LOOP
        city_mult := CASE c
            WHEN 'TP_HCM' THEN 1.00
            WHEN 'HANOI' THEN 0.95
            WHEN 'DANANG' THEN 0.85
        END;

        FOR ts IN SELECT unnest(enum_range(NULL::time_slot)) LOOP
            slot_mult := CASE ts
                WHEN 'DAYTIME_WEEKDAY' THEN 1.00
                WHEN 'EVENING_WEEKDAY' THEN 1.10
                WHEN 'NIGHT_WEEKDAY' THEN 1.30
                WHEN 'DAYTIME_WEEKEND' THEN 1.15
                WHEN 'EVENING_WEEKEND' THEN 1.25
            END;

            FOR lt IN SELECT unnest(enum_range(NULL::lead_time)) LOOP
                lead_mult := CASE lt
                    WHEN 'STANDARD' THEN 1.00
                    WHEN 'SOON' THEN 1.15
                    WHEN 'URGENT' THEN 1.35
                END;

                base_wage := ROUND(35000 * city_mult * slot_mult * lead_mult, -3);

                INSERT INTO wage_matrix (city, job_type, time_slot, lead_time, wage_per_hour, is_active)
                VALUES (c, 'Phục vụ bàn', ts, lt, base_wage, true);
            END LOOP;
        END LOOP;
    END LOOP;
END $$;
