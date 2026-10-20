-- =============================================================================
-- Flyway Migration: V1__init_schema.sql
-- Description: Khởi tạo toàn bộ lược đồ cơ sở dữ liệu cho nền tảng CANi (27 Tables)
-- Dựa trên tài liệu thiết kế: DbDesign Note.txt
-- =============================================================================

-- 1. Bật extension tạo UUID
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- 2. TẠO CÁC BẢNG NỀN TẢNG (USER & PROFILES)
-- =============================================================================

-- Table 1: users
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ADMIN', 'CSKH', 'FINANCE', 'EVENT_ORGANIZER', 'WORKER')),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED', 'SUSPENDED', 'PENDING_VERIFICATION')),
    full_name VARCHAR(255) NOT NULL,
    avatar_url TEXT,
    email_verified BOOLEAN DEFAULT FALSE,
    email_otp VARCHAR(10),
    email_otp_expires_at TIMESTAMPTZ,
    password_reset_otp VARCHAR(10),
    password_reset_otp_expires_at TIMESTAMPTZ,
    failed_login_attempts INT DEFAULT 0,
    locked_until TIMESTAMPTZ,
    fcm_token TEXT,
    language VARCHAR(10) DEFAULT 'vi',
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 2: skills (Kỹ năng)
CREATE TABLE skills (
    skill_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(100),
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 3: event_organizers (Nhà tổ chức sự kiện - EO)
CREATE TABLE event_organizers (
    eo_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    company_name VARCHAR(255),
    tax_id VARCHAR(50),
    address TEXT,
    verification_status VARCHAR(50) DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    verified_at TIMESTAMPTZ,
    verified_by UUID REFERENCES users(user_id),
    rating_avg NUMERIC(3, 2) DEFAULT 0.00,
    rating_count INT DEFAULT 0,
    total_events_completed INT DEFAULT 0,
    subscription_id UUID,
    subscription_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 4: workers (Người lao động / Nhân sự sự kiện)
CREATE TABLE workers (
    worker_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    cccd_number VARCHAR(20),
    cccd_front_url TEXT,
    cccd_back_url TEXT,
    selfie_url TEXT,
    kyc_status VARCHAR(50) DEFAULT 'PENDING' CHECK (kyc_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPICIOUS')),
    kyc_submitted_at TIMESTAMPTZ,
    kyc_reviewed_by UUID REFERENCES users(user_id),
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
    skill_tier VARCHAR(50) DEFAULT 'STARTER' CHECK (skill_tier IN ('STARTER', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER')),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 5: worker_availabilities (Lịch rảnh của worker)
CREATE TABLE worker_availabilities (
    availability_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    day_of_week INT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 3. TẠO CÁC BẢNG QUẢN LÝ SỰ KIỆN & CA LÀM (EVENTS & SHIFTS)
-- =============================================================================

-- Table 6: events
CREATE TABLE events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    eo_id UUID NOT NULL REFERENCES event_organizers(eo_id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    venue_address TEXT,
    venue_lat DOUBLE PRECISION,
    venue_lng DOUBLE PRECISION,
    event_date DATE,
    mode VARCHAR(50) DEFAULT 'MATCHING' CHECK (mode IN ('MATCHING', 'INVITE')),
    status VARCHAR(50) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'ONGOING', 'COMPLETED', 'CANCELLED', 'SUSPENDED')),
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

-- Table 7: shifts
CREATE TABLE shifts (
    shift_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
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
    status VARCHAR(50) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'FULLY_STAFFED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    risk_score INT DEFAULT 0,
    location_address TEXT,
    location_lat DOUBLE PRECISION,
    location_lng DOUBLE PRECISION,
    required_skills UUID[],
    min_reliability_score INT DEFAULT 0,
    max_distance_km DOUBLE PRECISION,
    min_experience_level VARCHAR(50) DEFAULT 'BEGINNER' CHECK (min_experience_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    arrival_time TIMESTAMPTZ,
    dress_code TEXT,
    checklist JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 4. TẠO CÁC BẢNG NHÓM LAO ĐỘNG (WORKER GROUPS)
-- =============================================================================

-- Table 8: worker_groups
CREATE TABLE worker_groups (
    group_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    skill_focus VARCHAR(100),
    leader_worker_id UUID REFERENCES workers(worker_id),
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

-- Table 9: worker_group_memberships
CREATE TABLE worker_group_memberships (
    membership_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES worker_groups(group_id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    role_in_group VARCHAR(50) DEFAULT 'MEMBER' CHECK (role_in_group IN ('LEADER', 'MEMBER')),
    role_title VARCHAR(100),
    status VARCHAR(50) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'REMOVED')),
    invited_by UUID REFERENCES workers(worker_id),
    joined_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (group_id, worker_id)
);

-- =============================================================================
-- 5. MATCHING, INVITE & PHÂN CÔNG (ASSIGNMENTS)
-- =============================================================================

-- Table 10: dispatch_proposals (Đề xuất matching từ hệ thống)
CREATE TABLE dispatch_proposals (
    proposal_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id UUID NOT NULL REFERENCES shifts(shift_id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    match_score NUMERIC(5, 2),
    score_breakdown JSONB,
    status VARCHAR(50) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    expires_at TIMESTAMPTZ,
    responded_at TIMESTAMPTZ,
    source VARCHAR(50) DEFAULT 'MATCHING' CHECK (source IN ('MATCHING', 'DIRECT', 'BULK')),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 11: invites (Lời mời trực tiếp từ EO)
CREATE TABLE invites (
    invite_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    shift_id UUID REFERENCES shifts(shift_id) ON DELETE CASCADE,
    target_type VARCHAR(50) DEFAULT 'SHIFT' CHECK (target_type IN ('SHIFT', 'GROUP')),
    group_id UUID REFERENCES worker_groups(group_id),
    code VARCHAR(50) UNIQUE NOT NULL,
    max_uses INT DEFAULT 1,
    used_count INT DEFAULT 0,
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 12: shift_assignments (Phân công worker vào ca)
CREATE TABLE shift_assignments (
    assignment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id UUID NOT NULL REFERENCES shifts(shift_id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    proposal_id UUID REFERENCES dispatch_proposals(proposal_id),
    invite_id UUID REFERENCES invites(invite_id),
    source VARCHAR(50) DEFAULT 'MATCHING' CHECK (source IN ('MATCHING', 'DIRECT', 'BULK', 'GROUP')),
    group_id UUID REFERENCES worker_groups(group_id),
    is_group_leader BOOLEAN DEFAULT FALSE,
    status VARCHAR(50) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    cancellation_reason TEXT,
    cancelled_by UUID REFERENCES users(user_id),
    cancelled_at TIMESTAMPTZ,
    cancellation_type VARCHAR(50),
    replaced_by UUID REFERENCES shift_assignments(assignment_id),
    is_replacement BOOLEAN DEFAULT FALSE,
    eo_confirmed BOOLEAN DEFAULT FALSE,
    eo_confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 13: attendance_logs (Điểm danh QR)
CREATE TABLE attendance_logs (
    attendance_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID UNIQUE NOT NULL REFERENCES shift_assignments(assignment_id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    checkin_time TIMESTAMPTZ,
    checkin_qr_hash VARCHAR(255),
    checkin_lat DOUBLE PRECISION,
    checkin_lng DOUBLE PRECISION,
    checkout_time TIMESTAMPTZ,
    checkout_qr_hash VARCHAR(255),
    hours_worked NUMERIC(5, 2) DEFAULT 0,
    is_on_time BOOLEAN DEFAULT TRUE,
    attendance_status VARCHAR(50) DEFAULT 'NOT_CHECKED_IN' CHECK (attendance_status IN ('NOT_CHECKED_IN', 'ON_TIME', 'LATE', 'CHECKED_OUT', 'NO_SHOW')),
    qr_generated_at TIMESTAMPTZ,
    qr_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 6. ĐÁNH GIÁ, KHIẾU NẠI & ĐIỂM TIN CẬY
-- =============================================================================

-- Table 14: reviews (Đánh giá 2 chiều)
CREATE TABLE reviews (
    review_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID REFERENCES shift_assignments(assignment_id),
    reviewer_id UUID NOT NULL REFERENCES users(user_id),
    reviewee_id UUID NOT NULL REFERENCES users(user_id),
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

-- Table 15: disputes (Khiếu nại)
CREATE TABLE disputes (
    dispute_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID REFERENCES shift_assignments(assignment_id),
    complainant_id UUID NOT NULL REFERENCES users(user_id),
    respondent_id UUID NOT NULL REFERENCES users(user_id),
    type VARCHAR(50) NOT NULL CHECK (type IN ('PAYMENT', 'CONDUCT', 'OVERTIME', 'SCOPE', 'APPEAL')),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    evidence JSONB,
    messages JSONB,
    status VARCHAR(50) DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED', 'IN_REVIEW', 'RESOLVED', 'ESCALATED')),
    resolution TEXT,
    resolved_by UUID REFERENCES users(user_id),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 16: reliability_logs (Lịch sử điểm tin cậy)
CREATE TABLE reliability_logs (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    change_amount INT NOT NULL,
    reason TEXT NOT NULL,
    reference_type VARCHAR(50),
    reference_id UUID,
    score_before INT NOT NULL,
    score_after INT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 7. THANH TOÁN & SUBSCRIPTION
-- =============================================================================

-- Table 17: subscriptions (Gói đăng ký của EO)
CREATE TABLE subscriptions (
    subscription_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID NOT NULL REFERENCES event_organizers(eo_id) ON DELETE CASCADE,
    plan_name VARCHAR(100) NOT NULL,
    plan_type VARCHAR(50) NOT NULL CHECK (plan_type IN ('INVITE_ONLY', 'MATCHING_PLUS')),
    duration_days INT NOT NULL,
    price NUMERIC(15, 2) NOT NULL,
    features JSONB,
    max_events INT,
    max_shifts INT,
    max_workers INT,
    events_used INT DEFAULT 0,
    shifts_used INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    starts_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Thêm khóa ngoại cho subscription_id trong bảng event_organizers
ALTER TABLE event_organizers 
    ADD CONSTRAINT fk_eo_subscription 
    FOREIGN KEY (subscription_id) REFERENCES subscriptions(subscription_id) ON DELETE SET NULL;

-- Table 18: payments (Ghi nhận trạng thái thanh toán)
CREATE TABLE payments (
    payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID REFERENCES events(event_id),
    shift_id UUID REFERENCES shifts(shift_id),
    assignment_id UUID REFERENCES shift_assignments(assignment_id),
    eo_id UUID NOT NULL REFERENCES event_organizers(eo_id),
    worker_id UUID NOT NULL REFERENCES workers(worker_id),
    agreed_amount NUMERIC(15, 2) NOT NULL,
    declared_amount NUMERIC(15, 2),
    status VARCHAR(50) DEFAULT 'NOT_DECLARED' CHECK (status IN ('NOT_DECLARED', 'DECLARED_PENDING_WORKER', 'CONFIRMED_FULL', 'CONFIRMED_PARTIAL', 'DISPUTED', 'UNVERIFIED')),
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

-- Table 19: payment_logs
CREATE TABLE payment_logs (
    log_id BIGSERIAL PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(payment_id) ON DELETE CASCADE,
    event VARCHAR(100) NOT NULL,
    payload JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 8. BẢNG LƯƠNG & GỢI Ý (WAGE MATRIX & RECOMMENDATION)
-- =============================================================================

-- Table 20: wage_matrix
CREATE TABLE wage_matrix (
    matrix_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city VARCHAR(50) NOT NULL CHECK (city IN ('TP.HCM', 'HANOI', 'DANANG')),
    job_type VARCHAR(100) NOT NULL,
    time_slot VARCHAR(50) NOT NULL CHECK (time_slot IN ('DAYTIME_WEEKDAY', 'EVENING_WEEKDAY', 'NIGHT_WEEKDAY', 'DAYTIME_WEEKEND', 'EVENING_WEEKEND')),
    lead_time VARCHAR(50) NOT NULL CHECK (lead_time IN ('STANDARD', 'SOON', 'URGENT')),
    wage_per_hour NUMERIC(12, 2) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 21: wage_recommendations
CREATE TABLE wage_recommendations (
    recommendation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city VARCHAR(50),
    job_type VARCHAR(100),
    time_slot VARCHAR(50),
    lead_time VARCHAR(50),
    suggested_wage NUMERIC(12, 2) NOT NULL,
    matched_row_id UUID REFERENCES wage_matrix(matrix_id),
    fallback_level INT DEFAULT 0,
    shift_id UUID REFERENCES shifts(shift_id),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 9. QUẢN LÝ QUAN HỆ & HỖ TRỢ (SUPPORT, NOTIFICATION, AUDIT)
-- =============================================================================

-- Table 22: worker_skill_points
CREATE TABLE worker_skill_points (
    sp_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
    job_type VARCHAR(100) NOT NULL,
    sp_verify INT DEFAULT 0,
    sp_experience INT DEFAULT 0,
    sp_rating INT DEFAULT 0,
    total_sp INT DEFAULT 0,
    tier VARCHAR(50) DEFAULT 'STARTER' CHECK (tier IN ('STARTER', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'MASTER')),
    total_shifts_completed INT DEFAULT 0,
    total_shifts_cancelled INT DEFAULT 0,
    avg_rating NUMERIC(3, 2) DEFAULT 0.00,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (worker_id, job_type)
);

-- Table 23: eo_workers (My Team của EO)
CREATE TABLE eo_workers (
    eo_worker_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    eo_id UUID NOT NULL REFERENCES event_organizers(eo_id) ON DELETE CASCADE,
    worker_id UUID NOT NULL REFERENCES workers(worker_id) ON DELETE CASCADE,
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

-- Table 24: notifications
CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('PROPOSAL', 'ASSIGNMENT', 'REMINDER', 'PAYMENT', 'DISPUTE', 'RATING', 'SYSTEM', 'GROUP')),
    is_read BOOLEAN DEFAULT FALSE,
    reference_type VARCHAR(50),
    reference_id UUID,
    data JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Table 25: block_lists
CREATE TABLE block_lists (
    block_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blocker_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    reason TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (blocker_id, blocked_id)
);

-- Table 26: system_configs
CREATE TABLE system_configs (
    config_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(100) NOT NULL UNIQUE,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(user_id)
);

-- Table 27: audit_logs
CREATE TABLE audit_logs (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(user_id),
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
-- 10. CHỈ MỤC TỐI ƯU HIỆU NĂNG (INDEXES)
-- =============================================================================
CREATE INDEX idx_users_role_status ON users(role, status);
CREATE INDEX idx_events_eo_status ON events(eo_id, status);
CREATE INDEX idx_shifts_event_status ON shifts(event_id, status);
CREATE INDEX idx_shift_assignments_shift ON shift_assignments(shift_id, status);
CREATE INDEX idx_shift_assignments_worker ON shift_assignments(worker_id, status);
CREATE INDEX idx_dispatch_proposals_worker ON dispatch_proposals(worker_id, status);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);
CREATE INDEX idx_payments_eo_worker ON payments(eo_id, worker_id, status);
