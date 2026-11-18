-- =============================================================================
-- Migration V34: Tự động cộng dồn thời hạn cho các gói dịch vụ mua gia hạn nối tiếp
-- =============================================================================

DO $$
DECLARE
    r RECORD;
    v_last_org UUID := NULL;
    v_chain_expiry TIMESTAMPTZ := NULL;
BEGIN
    FOR r IN (
        SELECT id, organizer_id, created_at, expires_at, COALESCE(duration_days, 30) AS duration_days
        FROM subscriptions
        WHERE status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP
        ORDER BY organizer_id, created_at ASC, id ASC
    ) LOOP
        IF v_last_org IS NULL OR v_last_org != r.organizer_id THEN
            v_last_org := r.organizer_id;
            v_chain_expiry := r.expires_at;
        ELSE
            -- Gói thứ 2 trở đi của cùng một ban tổ chức (EO)
            IF r.expires_at <= v_chain_expiry THEN
                v_chain_expiry := v_chain_expiry + (r.duration_days || ' days')::INTERVAL;
                UPDATE subscriptions
                SET expires_at = v_chain_expiry
                WHERE id = r.id;
            ELSE
                v_chain_expiry := r.expires_at;
            END IF;
        END IF;
    END LOOP;
END $$;
