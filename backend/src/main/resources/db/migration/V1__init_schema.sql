-- =============================================================================
-- Flyway Migration: V1__init_schema.sql
-- Description: Khởi tạo toàn bộ lược đồ cơ sở dữ liệu cho nền tảng CANi (27 Tables)
-- Kiến trúc: 23 PG ENUMs, PK chuẩn `id`, bảng số ít, liên kết GoTrue auth.users
-- =============================================================================

-- 1. Bật extension tạo UUID
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Function trigger tự động cập nhật updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE 'plpgsql';

-- =============================================================================
-- 2. TẠO 23 POSTGRES ENUM TYPES
-- =============================================================================

CREATE TYPE user_role AS ENUM ('ADMIN', 'CUSTOMER_CARE', 'EVENT_ORGANIZER', 'WORKER');
CREATE TYPE user_status AS ENUM ('ACTIVE', 'LOCKED', 'SUSPENDED', 'PENDING_VERIFICATION');
CREATE TYPE assignment_status AS ENUM ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW');
CREATE TYPE attendance_status AS ENUM ('NOT_CHECKED_IN', 'ON_TIME', 'LATE', 'CHECKED_OUT', 'NO_SHOW');
CREATE TYPE city AS ENUM ('TP_HCM', 'HANOI', 'DANANG');
CREATE TYPE dispute_status AS ENUM ('SUBMITTED', 'IN_REVIEW', 'RESOLVED', 'ESCALATED');
CREATE TYPE dispute_type AS ENUM ('PAYMENT', 'CONDUCT', 'OVERTIME', 'SCOPE', 'APPEAL');
CREATE TYPE event_mode AS ENUM ('MATCHING', 'INVITE');
CREATE TYPE event_status AS ENUM ('DRAFT', 'ACTIVE', 'ONGOING', 'COMPLETED', 'CANCELLED', 'SUSPENDED');
CREATE TYPE experience_level AS ENUM ('BEGINNER', 'INTERMEDIATE', 'ADVANCED');
CREATE TYPE group_member_role AS ENUM ('LEADER', 'MEMBER');
CREATE TYPE group_membership_status AS ENUM ('PENDING', 'ACTIVE', 'REMOVED');
CREATE TYPE kyc_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'SUSPICIOUS');
CREATE TYPE lead_time AS ENUM ('STANDARD', 'SOON', 'URGENT');
CREATE TYPE notif_type AS ENUM ('PROPOSAL', 'ASSIGNMENT', 'REMINDER', 'PAYMENT', 'DISPUTE', 'RATING', 'SYSTEM', 'GROUP');
CREATE TYPE payment_status AS ENUM ('NOT_DECLARED', 'DECLARED_PENDING_WORKER', 'CONFIRMED_FULL', 'CONFIRMED_PARTIAL', 'DISPUTED', 'UNVERIFIED');
CREATE TYPE proposal_status AS ENUM ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED');
CREATE TYPE shift_status AS ENUM ('DRAFT', 'ACTIVE', 'FULLY_STAFFED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');
CREATE TYPE skill_tier AS ENUM ('STARTER', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER');
CREATE TYPE subscription_plan AS ENUM ('INVITE_ONLY', 'MATCHING_PLUS');
CREATE TYPE subscription_status AS ENUM ('ACTIVE', 'EXPIRED', 'CANCELLED');
CREATE TYPE time_slot AS ENUM ('DAYTIME_WEEKDAY', 'EVENING_WEEKDAY', 'NIGHT_WEEKDAY', 'DAYTIME_WEEKEND', 'EVENING_WEEKEND');
CREATE TYPE verification_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');

-- =============================================================================
-- 3. TẠO CÁC BẢNG NỀN TẢNG (USER & PROFILES)
-- =============================================================================

-- Table 1: users (id là UUID khớp trực tiếp auth.users.id)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) UNIQUE,
    role user_role NOT NULL,
    status user_status NOT NULL DEFAULT 'ACTIVE',
    full_name VARCHAR(255) NOT NULL,
    avatar_url TEXT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    fcm_token TEXT,
    language VARCHAR(10) DEFAULT 'vi',
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 2: skills (Kỹ năng)
CREATE TABLE skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(100),
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 3: event_organizers (Nhà tổ chức sự kiện - EO)
CREATE TABLE event_organizers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    company_name VARCHAR(255),
    tax_id VARCHAR(50),
    address TEXT,
    verification_status verification_status DEFAULT 'PENDING',
    verified_at TIMESTAMPTZ,
    verified_by UUID REFERENCES users(id),
    rating_avg NUMERIC(3, 2) DEFAULT 0.00,
    rating_count INT DEFAULT 0,
    total_events_completed INT DEFAULT 0,
    subscription_id UUID,
    subscription_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_event_organizers_updated_at BEFORE UPDATE ON event_organizers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 4: workers (Người lao động / Nhân sự sự kiện)
CREATE TABLE workers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    cccd_number VARCHAR(20),
    cccd_front_url TEXT,
    cccd_back_url TEXT,
    selfie_url TEXT,
    kyc_status kyc_status DEFAULT 'PENDING',
    kyc_submitted_at TIMESTAMPTZ,
    kyc_reviewed_by UUID REFERENCES users(id),
    reliability_score INT DEFAULT 100,
    rating_avg NUMERIC(3, 2) DEFAULT 0.00,
    rating_count INT DEFAULT 0,
    total_shifts_completed INT DEFAULT 0,
    total_cancellations INT DEFAULT 0,
    total_noshows INT DEFAULT 0,
    is_online BOOLEAN DEFAULT FALSE,
    base_address TEXT,
    base_lat DOUBLE PRECISION,
    base_lng DOUBLE PRECISION,
    skills UUID[],
    years_experience INT DEFAULT 0,
    bio TEXT,
    min_wage NUMERIC(12, 2) DEFAULT 0,
    max_distance_km DOUBLE PRECISION,
    skill_tier skill_tier DEFAULT 'STARTER',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_workers_updated_at BEFORE UPDATE ON workers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 5: worker_availability (Lịch rảnh của worker - đổi tên số ít)
CREATE TABLE worker_availability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    day_of_week INT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_worker_availability_updated_at BEFORE UPDATE ON worker_availability FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- 4. TẠO CÁC BẢNG QUẢN LÝ SỰ KIỆN & CA LÀM (EVENTS & SHIFTS)
-- =============================================================================

-- Table 6: events
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    eo_id UUID NOT NULL REFERENCES event_organizers(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    venue_address TEXT,
    venue_lat DOUBLE PRECISION,
    venue_lng DOUBLE PRECISION,
    event_date DATE,
    mode event_mode DEFAULT 'MATCHING',
    status event_status DEFAULT 'DRAFT',
    readiness_score INT DEFAULT 0,
    total_shifts INT DEFAULT 0,
    total_required_workers INT DEFAULT 0,
    total_confirmed_workers INT DEFAULT 0,
    total_estimated_cost NUMERIC(15, 2) DEFAULT 0,
    total_paid NUMERIC(15, 2) DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);
CREATE TRIGGER trg_events_updated_at BEFORE UPDATE ON events FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 7: shifts
CREATE TABLE shifts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    job_type VARCHAR(100) NOT NULL,
    description TEXT,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    required_workers INT NOT NULL DEFAULT 1,
    confirmed_workers INT DEFAULT 0,
    remaining_workers INT DEFAULT 1,
    hourly_wage NUMERIC(12, 2) NOT NULL,
    allowance NUMERIC(12, 2) DEFAULT 0,
    total_cost NUMERIC(15, 2) DEFAULT 0,
    status shift_status DEFAULT 'DRAFT',
    risk_score INT DEFAULT 0,
    location_address TEXT,
    location_lat DOUBLE PRECISION,
    location_lng DOUBLE PRECISION,
    required_skills UUID[],
    min_reliability_score INT DEFAULT 0,
    max_distance_km DOUBLE PRECISION,
    min_experience_level experience_level DEFAULT 'BEGINNER',
    arrival_time TIMESTAMPTZ,
    dress_code TEXT,
    checklist JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_shifts_updated_at BEFORE UPDATE ON shifts FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- 5. TẠO CÁC BẢNG NHÓM LAO ĐỘNG (WORKER GROUPS)
-- =============================================================================

-- Table 8: worker_groups
CREATE TABLE worker_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    skill_focus VARCHAR(100),
    leader_worker_id UUID REFERENCES workers(id),
    min_size INT DEFAULT 5,
    max_size INT DEFAULT 20,
    availability JSONB,
    member_count INT DEFAULT 1,
    avg_rating NUMERIC(3, 2) DEFAULT 0.00,
    reliability_score INT DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_worker_groups_updated_at BEFORE UPDATE ON worker_groups FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 9: worker_group_memberships
CREATE TABLE worker_group_memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES worker_groups(id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    role_in_group group_member_role DEFAULT 'MEMBER',
    role_title VARCHAR(100),
    status group_membership_status DEFAULT 'PENDING',
    invited_by UUID REFERENCES workers(id),
    joined_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (group_id, worker_id)
);
CREATE TRIGGER trg_worker_group_memberships_updated_at BEFORE UPDATE ON worker_group_memberships FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- 6. MATCHING, INVITE & PHÂN CÔNG (ASSIGNMENTS)
-- =============================================================================

-- Table 10: dispatch_proposals
CREATE TABLE dispatch_proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id UUID NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    match_score NUMERIC(5, 2),
    score_breakdown JSONB,
    status proposal_status DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ,
    responded_at TIMESTAMPTZ,
    source VARCHAR(50) DEFAULT 'MATCHING' CHECK (source IN ('MATCHING', 'DIRECT', 'BULK')),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 11: invites
CREATE TABLE invites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    shift_id UUID REFERENCES shifts(id) ON DELETE CASCADE,
    target_type VARCHAR(50) DEFAULT 'SHIFT' CHECK (target_type IN ('SHIFT', 'GROUP')),
    group_id UUID REFERENCES worker_groups(id),
    code VARCHAR(50) UNIQUE NOT NULL,
    max_uses INT DEFAULT 1,
    used_count INT DEFAULT 0,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 12: shift_assignments
CREATE TABLE shift_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id UUID NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    proposal_id UUID REFERENCES dispatch_proposals(id),
    invite_id UUID REFERENCES invites(id),
    source VARCHAR(50) DEFAULT 'MATCHING' CHECK (source IN ('MATCHING', 'DIRECT', 'BULK', 'GROUP')),
    group_id UUID REFERENCES worker_groups(id),
    is_group_leader BOOLEAN DEFAULT FALSE,
    status assignment_status DEFAULT 'PENDING',
    cancellation_reason TEXT,
    cancelled_by UUID REFERENCES users(id),
    cancelled_at TIMESTAMPTZ,
    cancellation_type VARCHAR(50),
    replaced_by UUID REFERENCES shift_assignments(id),
    is_replacement BOOLEAN DEFAULT FALSE,
    eo_confirmed BOOLEAN DEFAULT FALSE,
    eo_confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_shift_assignments_updated_at BEFORE UPDATE ON shift_assignments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 13: attendance_logs
CREATE TABLE attendance_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID UNIQUE NOT NULL REFERENCES shift_assignments(id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    checkin_time TIMESTAMPTZ,
    checkin_qr_hash VARCHAR(255),
    checkin_lat DOUBLE PRECISION,
    checkin_lng DOUBLE PRECISION,
    checkout_time TIMESTAMPTZ,
    checkout_qr_hash VARCHAR(255),
    hours_worked NUMERIC(5, 2) DEFAULT 0,
    is_on_time BOOLEAN DEFAULT TRUE,
    attendance_status attendance_status DEFAULT 'NOT_CHECKED_IN',
    qr_generated_at TIMESTAMPTZ,
    qr_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_attendance_logs_updated_at BEFORE UPDATE ON attendance_logs FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- 7. ĐÁNH GIÁ, KHIẾU NẠI & ĐIỂM TIN CẬY
-- =============================================================================

-- Table 14: reviews
CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID REFERENCES shift_assignments(id),
    reviewer_id UUID NOT NULL REFERENCES users(id),
    reviewee_id UUID NOT NULL REFERENCES users(id),
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    criteria_work_completion INT,
    criteria_punctuality INT,
    criteria_professionalism INT,
    criteria_venue_rules INT,
    criteria_teamwork INT,
    criteria_shift_clarity INT,
    criteria_timing INT,
    criteria_work_environment INT,
    criteria_support INT,
    criteria_payment_timing INT,
    comment TEXT,
    tags TEXT[],
    is_auto_closed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 15: disputes
CREATE TABLE disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID REFERENCES shift_assignments(id),
    complainant_id UUID NOT NULL REFERENCES users(id),
    respondent_id UUID NOT NULL REFERENCES users(id),
    type dispute_type NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    evidence JSONB,
    messages JSONB,
    status dispute_status DEFAULT 'SUBMITTED',
    resolution TEXT,
    resolved_by UUID REFERENCES users(id),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_disputes_updated_at BEFORE UPDATE ON disputes FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 16: reliability_logs
CREATE TABLE reliability_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    change_amount INT NOT NULL,
    reason TEXT NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    score_before INT NOT NULL,
    score_after INT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 8. THANH TOÁN & SUBSCRIPTION
-- =============================================================================

-- Table 17: subscriptions
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID NOT NULL REFERENCES event_organizers(id) ON DELETE CASCADE,
    plan_name VARCHAR(100) NOT NULL,
    plan_type subscription_plan NOT NULL,
    duration_days INT NOT NULL,
    price NUMERIC(15, 2) NOT NULL,
    features JSONB,
    max_events INT,
    max_shifts INT,
    max_workers INT,
    events_used INT DEFAULT 0,
    shifts_used INT DEFAULT 0,
    status subscription_status DEFAULT 'ACTIVE',
    starts_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_subscriptions_updated_at BEFORE UPDATE ON subscriptions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Khóa ngoại từ event_organizers sang subscriptions
ALTER TABLE event_organizers 
    ADD CONSTRAINT fk_eo_subscription 
    FOREIGN KEY (subscription_id) REFERENCES subscriptions(id) ON DELETE SET NULL;

-- Table 18: payments
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID REFERENCES events(id),
    shift_id UUID REFERENCES shifts(id),
    assignment_id UUID REFERENCES shift_assignments(id),
    eo_id UUID NOT NULL REFERENCES event_organizers(id),
    worker_id UUID NOT NULL REFERENCES workers(id),
    agreed_amount NUMERIC(15, 2) NOT NULL,
    declared_amount NUMERIC(15, 2),
    status payment_status DEFAULT 'NOT_DECLARED',
    payment_method VARCHAR(50),
    declared_at TIMESTAMPTZ,
    external_ref VARCHAR(255),
    payment_proof_url TEXT,
    worker_confirmed_at TIMESTAMPTZ,
    worker_confirm_status VARCHAR(50),
    worker_comment TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 19: payment_logs
CREATE TABLE payment_logs (
    id BIGSERIAL PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    event VARCHAR(100) NOT NULL,
    payload JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 9. BẢNG LƯƠNG & GỢI Ý (WAGE MATRIX & RECOMMENDATION)
-- =============================================================================

-- Table 20: wage_matrix
CREATE TABLE wage_matrix (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city city NOT NULL,
    job_type VARCHAR(100) NOT NULL,
    time_slot time_slot NOT NULL,
    lead_time lead_time NOT NULL,
    wage_per_hour NUMERIC(12, 2) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_wage_matrix_updated_at BEFORE UPDATE ON wage_matrix FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 21: wage_recommendations
CREATE TABLE wage_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city city,
    job_type VARCHAR(100),
    time_slot time_slot,
    lead_time lead_time,
    suggested_wage NUMERIC(12, 2) NOT NULL,
    matched_row_id UUID REFERENCES wage_matrix(id),
    fallback_level INT DEFAULT 0,
    shift_id UUID REFERENCES shifts(id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 10. QUẢN LÝ QUAN HỆ & HỖ TRỢ (SUPPORT, NOTIFICATION, AUDIT)
-- =============================================================================

-- Table 22: worker_skill_points
CREATE TABLE worker_skill_points (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    job_type VARCHAR(100) NOT NULL,
    sp_verify INT DEFAULT 0,
    sp_experience INT DEFAULT 0,
    sp_rating INT DEFAULT 0,
    total_sp INT DEFAULT 0,
    tier skill_tier DEFAULT 'STARTER',
    total_shifts_completed INT DEFAULT 0,
    total_shifts_cancelled INT DEFAULT 0,
    avg_rating NUMERIC(3, 2) DEFAULT 0.00,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (worker_id, job_type)
);
CREATE TRIGGER trg_worker_skill_points_updated_at BEFORE UPDATE ON worker_skill_points FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 23: eo_workers
CREATE TABLE eo_workers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    eo_id UUID NOT NULL REFERENCES event_organizers(id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    total_shifts_together INT DEFAULT 0,
    completion_rate NUMERIC(5, 2) DEFAULT 0.00,
    avg_rating_given NUMERIC(3, 2) DEFAULT 0.00,
    last_worked_at TIMESTAMPTZ,
    internal_note TEXT,
    is_favorited BOOLEAN DEFAULT FALSE,
    is_blacklisted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (eo_id, worker_id)
);
CREATE TRIGGER trg_eo_workers_updated_at BEFORE UPDATE ON eo_workers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 24: notifications
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    type notif_type NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    reference_type VARCHAR(50),
    reference_id UUID,
    data JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 25: block_list (đổi tên số ít)
CREATE TABLE block_list (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (blocker_id, blocked_id)
);

-- Table 26: system_config (đổi tên số ít)
CREATE TABLE system_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(100) NOT NULL UNIQUE,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id)
);
CREATE TRIGGER trg_system_config_updated_at BEFORE UPDATE ON system_config FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Table 27: audit_logs
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    old_values JSONB,
    new_values JSONB,
    ip_address VARCHAR(50),
    user_agent TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 11. CHỈ MỤC TỐI ƯU HIỆU NĂNG (INDEXES)
-- =============================================================================
CREATE INDEX idx_users_role_status ON users(role, status);
CREATE INDEX idx_events_eo_status ON events(eo_id, status);
CREATE INDEX idx_shifts_event_status ON shifts(event_id, status);
CREATE INDEX idx_shift_assignments_shift ON shift_assignments(shift_id, status);
CREATE INDEX idx_shift_assignments_worker ON shift_assignments(worker_id, status);
CREATE INDEX idx_dispatch_proposals_worker ON dispatch_proposals(worker_id, status);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);
CREATE INDEX idx_payments_eo_worker ON payments(eo_id, worker_id, status);
