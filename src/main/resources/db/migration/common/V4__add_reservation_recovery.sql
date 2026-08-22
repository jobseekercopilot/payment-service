ALTER TABLE ai_token_reservations
    ADD COLUMN operation_key VARCHAR(200);
ALTER TABLE ai_token_reservations
    ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ai_token_reservations
    ADD COLUMN last_transition_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ai_token_reservations
    ADD COLUMN last_transition_reason VARCHAR(128);
ALTER TABLE ai_token_reservations
    ADD COLUMN reconciliation_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ai_token_reservations
    ADD COLUMN last_reconciliation_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ai_token_reservations
    ADD COLUMN reconciliation_error_code VARCHAR(128);

UPDATE ai_token_reservations
SET operation_key = 'LEGACY:' || id,
    expires_at = created_at,
    last_transition_at = COALESCE(committed_at, released_at, created_at),
    last_transition_reason = CASE status
        WHEN 'COMMITTED' THEN 'LEGACY_COMMITTED'
        WHEN 'RELEASED' THEN 'LEGACY_RELEASED'
        WHEN 'FAILED' THEN 'LEGACY_FAILED'
        ELSE 'LEGACY_RESERVED'
    END;

ALTER TABLE ai_token_reservations
    ALTER COLUMN operation_key SET NOT NULL;
ALTER TABLE ai_token_reservations
    ALTER COLUMN expires_at SET NOT NULL;
ALTER TABLE ai_token_reservations
    ALTER COLUMN last_transition_at SET NOT NULL;
ALTER TABLE ai_token_reservations
    ALTER COLUMN last_transition_reason SET NOT NULL;
ALTER TABLE ai_token_reservations
    ADD CONSTRAINT uq_ai_token_reservations_owner_operation
        UNIQUE (user_id, operation_key);
ALTER TABLE ai_token_reservations
    ADD CONSTRAINT ck_ai_token_reservation_expiry
        CHECK (expires_at >= created_at);
ALTER TABLE ai_token_reservations
    ADD CONSTRAINT ck_ai_token_reservation_reconciliation_attempts
        CHECK (reconciliation_attempts >= 0);

CREATE INDEX idx_ai_token_reservations_expiry
    ON ai_token_reservations (status, expires_at);
