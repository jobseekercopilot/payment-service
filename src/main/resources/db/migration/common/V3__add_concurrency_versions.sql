ALTER TABLE ai_token_wallets
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;

ALTER TABLE ai_token_reservations
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
