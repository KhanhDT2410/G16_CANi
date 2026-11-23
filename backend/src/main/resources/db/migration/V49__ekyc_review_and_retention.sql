-- Historical review times cannot be reconstructed from updated_at.
ALTER TABLE worker_ekyc_verifications
    ADD COLUMN legal_hold boolean NOT NULL DEFAULT false,
    ADD COLUMN reviewed_at timestamptz;
ALTER TABLE eo_ekyc_verifications
    ADD COLUMN legal_hold boolean NOT NULL DEFAULT false,
    ADD COLUMN reviewed_at timestamptz;

-- These records are served by the authorized backend only. Supabase SDK clients
-- must not obtain images or OCR data directly through the public schema.
ALTER TABLE worker_ekyc_verifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE eo_ekyc_verifications ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON worker_ekyc_verifications, eo_ekyc_verifications FROM PUBLIC, anon, authenticated;
