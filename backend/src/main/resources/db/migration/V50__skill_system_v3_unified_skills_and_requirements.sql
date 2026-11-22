-- CANi Skill System V3: Chuan hoa WorkerSkillPoint (skill_id, rating_count),
-- Shift (performed_skill_id, custom_skill_notes), ShiftAssignment idempotency,
-- va bang shift_skill_requirements (REQUIRED / PREFERRED + min_verification_tier + cert).
-- Khong drop worker_skill_points.job_type hay shifts.required_skills de giu tuong thich nguoc.

-- ============================================================================
-- 1. CHUAN HOA WORKER_SKILL_POINTS
-- ============================================================================
ALTER TABLE worker_skill_points
    ADD COLUMN IF NOT EXISTS skill_id UUID REFERENCES skills(id);

ALTER TABLE worker_skill_points
    ADD COLUMN IF NOT EXISTS rating_count INT NOT NULL DEFAULT 0;

ALTER TABLE worker_skill_points
    ALTER COLUMN avg_rating SET DEFAULT 0.00;

-- Backfill skill_id tu job_type theo ten skill (khong tao duplicate (worker_id, skill_id))
WITH matched AS (
    SELECT DISTINCT ON (wsp.worker_id, s.id)
           wsp.id AS sp_id,
           s.id AS target_skill_id
    FROM worker_skill_points wsp
    JOIN skills s ON LOWER(TRIM(wsp.job_type)) = LOWER(TRIM(s.name))
    WHERE wsp.skill_id IS NULL
    ORDER BY wsp.worker_id, s.id, wsp.total_sp DESC, wsp.updated_at DESC
)
UPDATE worker_skill_points wsp
SET skill_id = m.target_skill_id
FROM matched m
WHERE wsp.id = m.sp_id
  AND NOT EXISTS (
      SELECT 1
      FROM worker_skill_points existing
      WHERE existing.worker_id = wsp.worker_id
        AND existing.skill_id = m.target_skill_id
  );

-- Loai bo gia tri avg_rating = 5.00 mac dinh neu chua co bat ky danh gia that nao
UPDATE worker_skill_points
SET avg_rating = 0.00
WHERE COALESCE(sp_rating, 0) = 0
  AND COALESCE(rating_count, 0) = 0
  AND avg_rating = 5.00;

-- Bao toan so luong danh gia toi thieu cho ban ghi da co sp_rating truoc do
UPDATE worker_skill_points
SET rating_count = 1
WHERE COALESCE(sp_rating, 0) > 0
  AND COALESCE(rating_count, 0) = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_worker_skill_points_worker_skill
    ON worker_skill_points (worker_id, skill_id)
    WHERE skill_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_worker_skill_points_skill_id
    ON worker_skill_points (skill_id);

-- ============================================================================
-- 2. SHIFT PERFORMED_SKILL_ID & CUSTOM_SKILL_NOTES
-- ============================================================================
ALTER TABLE shifts
    ADD COLUMN IF NOT EXISTS performed_skill_id UUID REFERENCES skills(id);

ALTER TABLE shifts
    ADD COLUMN IF NOT EXISTS custom_skill_notes TEXT;

-- Chi backfill performed_skill_id khi mapping la duy nhat va dang tin cay:
-- Case A: job_type trung khop chinh xac voi 1 active skill, va required_skills rong hoac chi gom dung skill do
UPDATE shifts s
SET performed_skill_id = sk.id
FROM skills sk
WHERE s.performed_skill_id IS NULL
  AND sk.is_active = TRUE
  AND LOWER(TRIM(s.job_type)) = LOWER(TRIM(sk.name))
  AND (
      s.required_skills IS NULL
      OR COALESCE(array_length(s.required_skills, 1), 0) = 0
      OR (array_length(s.required_skills, 1) = 1 AND s.required_skills[1] = sk.id)
  );

-- Case B: shift chua match Case A, co dung 1 required_skill active va job_type khong khop skill khac
UPDATE shifts s
SET performed_skill_id = sk.id
FROM skills sk
WHERE s.performed_skill_id IS NULL
  AND s.required_skills IS NOT NULL
  AND array_length(s.required_skills, 1) = 1
  AND sk.id = s.required_skills[1]
  AND sk.is_active = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM skills other
      WHERE other.is_active = TRUE
        AND LOWER(TRIM(other.name)) = LOWER(TRIM(s.job_type))
        AND other.id <> sk.id
  );

CREATE INDEX IF NOT EXISTS idx_shifts_performed_skill_id
    ON shifts (performed_skill_id);

-- ============================================================================
-- 3. SHIFT_ASSIGNMENTS IDEMPOTENCY & AUDIT OVERRIDE
-- ============================================================================
ALTER TABLE shift_assignments
    ADD COLUMN IF NOT EXISTS confirmed_performed_skill_id UUID REFERENCES skills(id);

ALTER TABLE shift_assignments
    ADD COLUMN IF NOT EXISTS sp_experience_awarded BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE shift_assignments
    ADD COLUMN IF NOT EXISTS sp_experience_awarded_at TIMESTAMPTZ;

ALTER TABLE shift_assignments
    ADD COLUMN IF NOT EXISTS sp_rating_awarded BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE shift_assignments
    ADD COLUMN IF NOT EXISTS invite_override_reason TEXT;

UPDATE shift_assignments sa
SET sp_experience_awarded = TRUE,
    sp_experience_awarded_at = COALESCE(sa.eo_confirmed_at, sa.checkout_time, sa.created_at),
    confirmed_performed_skill_id = COALESCE(sa.confirmed_performed_skill_id, s.performed_skill_id)
FROM shifts s
WHERE sa.shift_id = s.id
  AND sa.status = 'COMPLETED'
  AND sa.sp_experience_awarded = FALSE;

UPDATE shift_assignments
SET sp_rating_awarded = TRUE
WHERE eo_rating IS NOT NULL
  AND sp_rating_awarded = FALSE;

CREATE INDEX IF NOT EXISTS idx_shift_assignments_sp_awarded
    ON shift_assignments (shift_id, sp_experience_awarded);

-- ============================================================================
-- 4. SHIFT_SKILL_REQUIREMENTS (STRUCTURED REQUIRED & PREFERRED REQUIREMENTS)
-- ============================================================================
CREATE TABLE IF NOT EXISTS shift_skill_requirements (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shift_id                   UUID NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
    skill_id                   UUID NOT NULL REFERENCES skills(id) ON DELETE RESTRICT,
    requirement_type           VARCHAR(20) NOT NULL DEFAULT 'REQUIRED'
        CHECK (requirement_type IN ('REQUIRED', 'PREFERRED')),
    min_verification_tier      VARCHAR(30) NOT NULL DEFAULT 'SELF_DECLARED'
        CHECK (min_verification_tier IN ('SELF_DECLARED', 'AI_DOCUMENT', 'PLATFORM_PROVEN')),
    requires_valid_certificate BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shift_skill_requirement UNIQUE (shift_id, skill_id)
);

CREATE INDEX IF NOT EXISTS idx_shift_skill_req_shift
    ON shift_skill_requirements (shift_id);

CREATE INDEX IF NOT EXISTS idx_shift_skill_req_skill
    ON shift_skill_requirements (skill_id);

ALTER TABLE shift_skill_requirements ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'shift_skill_requirements' AND policyname = 'deny_anon'
    ) THEN
        CREATE POLICY "deny_anon" ON shift_skill_requirements FOR ALL TO anon USING (false);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE tablename = 'shift_skill_requirements' AND policyname = 'deny_authenticated'
    ) THEN
        CREATE POLICY "deny_authenticated" ON shift_skill_requirements FOR ALL TO authenticated USING (false);
    END IF;
END $$;

-- Backfill moi skill trong shifts.required_skills -> REQUIRED + SELF_DECLARED (khong ep len AI_DOCUMENT)
INSERT INTO shift_skill_requirements (
    shift_id, skill_id, requirement_type, min_verification_tier, requires_valid_certificate
)
SELECT DISTINCT
    s.id,
    req_skill_id,
    'REQUIRED',
    'SELF_DECLARED',
    FALSE
FROM shifts s
CROSS JOIN LATERAL unnest(s.required_skills) AS req_skill_id
JOIN skills sk ON sk.id = req_skill_id
WHERE s.required_skills IS NOT NULL
ON CONFLICT (shift_id, skill_id) DO NOTHING;
