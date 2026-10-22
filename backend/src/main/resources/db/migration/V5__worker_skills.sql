-- =============================================================================
-- Flyway Migration: V5__worker_skills.sql
-- Description: Per-skill verification records for Workers (BR-16, BR-17, BR-65; UC-32/42/43/46/47)
--   * worker_skills replaces workers.skills UUID[] as the source of truth: it stores
--     proficiency, verification tier, awarded SP_verify, expiry and evidence per skill.
--   * workers.skills is kept (not dropped) for backward compatibility and is no longer written.
-- =============================================================================

-- UC-32 defines 4 proficiency levels; V1 only had 3.
ALTER TYPE experience_level ADD VALUE IF NOT EXISTS 'EXPERT';

CREATE TABLE IF NOT EXISTS worker_skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    worker_id UUID NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills(id) ON DELETE RESTRICT,
    proficiency experience_level NOT NULL DEFAULT 'BEGINNER',
    verification_tier VARCHAR(20) NOT NULL DEFAULT 'SELF_DECLARED'
        CHECK (verification_tier IN ('SELF_DECLARED', 'AI_DOCUMENT', 'PLATFORM_PROVEN')),
    sp_verify_awarded INT NOT NULL DEFAULT 0 CHECK (sp_verify_awarded >= 0),
    verified_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    evidence_request_id UUID REFERENCES skill_verification_requests(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_worker_skills_worker_skill UNIQUE (worker_id, skill_id)
);

CREATE INDEX IF NOT EXISTS idx_worker_skills_worker ON worker_skills(worker_id);

DROP TRIGGER IF EXISTS trg_worker_skills_updated_at ON worker_skills;
CREATE TRIGGER trg_worker_skills_updated_at BEFORE UPDATE ON worker_skills
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE worker_skills ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "deny_anon" ON worker_skills;
DROP POLICY IF EXISTS "deny_authenticated" ON worker_skills;
CREATE POLICY "deny_anon" ON worker_skills FOR ALL TO anon USING (false);
CREATE POLICY "deny_authenticated" ON worker_skills FOR ALL TO authenticated USING (false);
GRANT ALL ON TABLE worker_skills TO postgres, service_role;

-- Worker's own submission notes; review_notes is reserved for the reviewer.
ALTER TABLE skill_verification_requests ADD COLUMN IF NOT EXISTS worker_notes TEXT;

-- -----------------------------------------------------------------------------
-- Backfill 1: every skill in workers.skills becomes a Self-Declared record.
-- -----------------------------------------------------------------------------
INSERT INTO worker_skills (worker_id, skill_id)
SELECT w.id, s.skill_id
FROM workers w
CROSS JOIN LATERAL unnest(w.skills) AS s(skill_id)
JOIN skills k ON k.id = s.skill_id
WHERE w.skills IS NOT NULL
ON CONFLICT (worker_id, skill_id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Backfill 2: approved AI verifications become AI_DOCUMENT (+25, expires after 24 months - BR-17).
-- -----------------------------------------------------------------------------
INSERT INTO worker_skills (worker_id, skill_id, verification_tier, sp_verify_awarded,
                           verified_at, expires_at, evidence_request_id)
SELECT DISTINCT ON (r.worker_id, r.skill_id)
       r.worker_id, r.skill_id, 'AI_DOCUMENT', 25,
       COALESCE(r.updated_at, r.created_at),
       COALESCE(r.updated_at, r.created_at) + INTERVAL '24 months',
       r.id
FROM skill_verification_requests r
WHERE r.status = 'APPROVED' AND r.skill_id IS NOT NULL
ORDER BY r.worker_id, r.skill_id, r.updated_at DESC
ON CONFLICT (worker_id, skill_id) DO UPDATE
    SET verification_tier   = EXCLUDED.verification_tier,
        sp_verify_awarded   = EXCLUDED.sp_verify_awarded,
        verified_at         = EXCLUDED.verified_at,
        expires_at          = EXCLUDED.expires_at,
        evidence_request_id = EXCLUDED.evidence_request_id;

-- -----------------------------------------------------------------------------
-- Backfill 3: recompute SP_verify from worker_skills (job_type = skill name) and
-- re-derive tiers with BR-64 thresholds (old code used 250/500/750/1000/2000).
-- One-off copy of the thresholds; runtime logic lives in SkillTier.java.
-- -----------------------------------------------------------------------------
UPDATE worker_skill_points p
SET sp_verify = COALESCE((
        SELECT SUM(ws.sp_verify_awarded)
        FROM worker_skills ws JOIN skills k ON k.id = ws.skill_id
        WHERE ws.worker_id = p.worker_id AND k.name = p.job_type), 0);

UPDATE worker_skill_points
SET total_sp = COALESCE(sp_verify, 0) + COALESCE(sp_experience, 0) + COALESCE(sp_rating, 0);

UPDATE worker_skill_points
SET tier = (CASE
        WHEN total_sp >= 900 THEN 'MASTER'
        WHEN total_sp >= 700 THEN 'DIAMOND'
        WHEN total_sp >= 500 THEN 'PLATINUM'
        WHEN total_sp >= 350 THEN 'GOLD'
        WHEN total_sp >= 200 THEN 'SILVER'
        WHEN total_sp >= 100 THEN 'BRONZE'
        ELSE 'STARTER' END)::skill_tier;

UPDATE workers w
SET skill_tier = COALESCE((SELECT MAX(p.tier) FROM worker_skill_points p WHERE p.worker_id = w.id),
                          'STARTER'::skill_tier);
