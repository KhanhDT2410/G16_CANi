-- =============================================================================
-- Flyway Migration: V74__refactor_wage_matrix_region_job_tier.sql
-- Description: Tái cấu trúc Wage Matrix sang mô hình 4 Vùng x 6 Nhóm bậc lương (Region x JobTier)
--              tuân thủ sàn lương tối thiểu giờ theo Nghị định 293/2025/NĐ-CP (hiệu lực 01/01/2026).
-- =============================================================================

-- 1. Tạo bảng cấu hình 4 vùng lương pháp lý (Nghị định 293/2025/NĐ-CP)
CREATE TABLE IF NOT EXISTS wage_regions (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    legal_floor_hourly NUMERIC(12, 2) NOT NULL,
    market_multiplier NUMERIC(5, 2) NOT NULL DEFAULT 1.00,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_wage_regions_code CHECK (code IN ('REGION_1', 'REGION_2', 'REGION_3', 'REGION_4')),
    CONSTRAINT chk_wage_regions_floor_positive CHECK (legal_floor_hourly > 0),
    CONSTRAINT chk_wage_regions_market_mult CHECK (market_multiplier >= 0.50)
);

DROP TRIGGER IF EXISTS trg_wage_regions_updated_at ON wage_regions;
CREATE TRIGGER trg_wage_regions_updated_at
    BEFORE UPDATE ON wage_regions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

INSERT INTO wage_regions (code, name, legal_floor_hourly, market_multiplier, description, is_active) VALUES
('REGION_1', 'Vùng I', 25500.00, 1.00, 'Hà Nội, TP.HCM, Hải Phòng, Bình Dương, Đồng Nai và các địa bàn Vùng I theo NĐ 293/2025/NĐ-CP', TRUE),
('REGION_2', 'Vùng II', 22700.00, 1.00, 'Đà Nẵng, Cần Thơ và các đô thị/khu vực Vùng II theo NĐ 293/2025/NĐ-CP', TRUE),
('REGION_3', 'Vùng III', 20000.00, 1.00, 'Các thành phố trực thuộc tỉnh và khu vực Vùng III theo NĐ 293/2025/NĐ-CP', TRUE),
('REGION_4', 'Vùng IV', 17800.00, 1.00, 'Các địa bàn còn lại thuộc Vùng IV theo NĐ 293/2025/NĐ-CP', TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    legal_floor_hourly = EXCLUDED.legal_floor_hourly,
    market_multiplier = EXCLUDED.market_multiplier,
    description = EXCLUDED.description,
    is_active = EXCLUDED.is_active;

-- 2. Tái cấu trúc bảng wage_matrix từ 4 chiều cũ sang Region x JobTier
DELETE FROM wage_matrix;

ALTER TABLE wage_matrix
    DROP COLUMN IF EXISTS city,
    DROP COLUMN IF EXISTS job_type,
    DROP COLUMN IF EXISTS time_slot,
    DROP COLUMN IF EXISTS lead_time,
    DROP COLUMN IF EXISTS wage_per_hour;

ALTER TABLE wage_matrix
    ADD COLUMN IF NOT EXISTS region_code VARCHAR(30) NOT NULL DEFAULT 'REGION_1',
    ADD COLUMN IF NOT EXISTS job_tier VARCHAR(30) NOT NULL DEFAULT 'TIER_1_GENERAL',
    ADD COLUMN IF NOT EXISTS tier_label VARCHAR(120) NOT NULL DEFAULT 'Phổ thông',
    ADD COLUMN IF NOT EXISTS description VARCHAR(500),
    ADD COLUMN IF NOT EXISTS min_multiplier NUMERIC(5, 2) NOT NULL DEFAULT 1.00,
    ADD COLUMN IF NOT EXISTS recommended_multiplier NUMERIC(5, 2) NOT NULL DEFAULT 1.15,
    ADD COLUMN IF NOT EXISTS max_multiplier NUMERIC(5, 2) NOT NULL DEFAULT 1.30,
    ADD COLUMN IF NOT EXISTS override_base_wage NUMERIC(12, 2);

ALTER TABLE wage_matrix
    ALTER COLUMN is_active SET DEFAULT TRUE,
    ALTER COLUMN is_active SET NOT NULL;

ALTER TABLE wage_matrix
    ADD CONSTRAINT fk_wage_matrix_region FOREIGN KEY (region_code) REFERENCES wage_regions(code),
    ADD CONSTRAINT uq_wage_matrix_region_tier UNIQUE (region_code, job_tier),
    ADD CONSTRAINT chk_wage_matrix_job_tier CHECK (job_tier IN (
        'TIER_1_GENERAL',
        'TIER_2_SERVICE',
        'TIER_3_PHYSICAL',
        'TIER_4_PROMOTION',
        'TIER_5_TECHNICAL',
        'TIER_6_SPECIALIST'
    )),
    ADD CONSTRAINT chk_wage_matrix_multipliers CHECK (
        min_multiplier >= 1.00
        AND recommended_multiplier >= min_multiplier
        AND max_multiplier >= recommended_multiplier
    );

-- 3. Seed dữ liệu chuẩn Bảng A cho đủ 4 vùng x 6 nhóm công việc (24 dòng)
INSERT INTO wage_matrix (
    region_code,
    job_tier,
    tier_label,
    description,
    min_multiplier,
    recommended_multiplier,
    max_multiplier,
    override_base_wage,
    is_active
)
SELECT
    r.code,
    t.job_tier,
    t.tier_label,
    t.description,
    t.min_multiplier,
    t.recommended_multiplier,
    t.max_multiplier,
    NULL,
    TRUE
FROM wage_regions r
CROSS JOIN (
    VALUES
        ('TIER_1_GENERAL', 'Phổ thông', 'Phát tờ rơi, hậu cần nhẹ, trực khu vực', 1.00, 1.15, 1.30),
        ('TIER_2_SERVICE', 'Giao tiếp & Dịch vụ', 'Lễ tân, check-in, hướng dẫn khách, phục vụ bàn, thu ngân', 1.30, 1.55, 1.80),
        ('TIER_3_PHYSICAL', 'Thể lực / An ninh', 'Bốc xếp, setup sân khấu, bảo vệ, an ninh', 1.50, 1.85, 2.20),
        ('TIER_4_PROMOTION', 'PG / PB bán hàng & Pha chế', 'PG/PB bán hàng, sampling, roadshow, pha chế (Bartender/Barista)', 2.00, 2.80, 4.00),
        ('TIER_5_TECHNICAL', 'Điều phối & Kỹ thuật', 'Điều phối sự kiện (Coordinator), kỹ thuật âm thanh - ánh sáng', 2.50, 3.75, 5.00),
        ('TIER_6_SPECIALIST', 'Chuyên nghiệp / Ngoại ngữ', 'PG/PB sự kiện chuyên nghiệp, có ngoại ngữ, MC song ngữ', 4.50, 6.50, 10.00)
) AS t(job_tier, tier_label, description, min_multiplier, recommended_multiplier, max_multiplier)
ON CONFLICT (region_code, job_tier) DO UPDATE SET
    tier_label = EXCLUDED.tier_label,
    description = EXCLUDED.description,
    min_multiplier = EXCLUDED.min_multiplier,
    recommended_multiplier = EXCLUDED.recommended_multiplier,
    max_multiplier = EXCLUDED.max_multiplier,
    is_active = EXCLUDED.is_active;
