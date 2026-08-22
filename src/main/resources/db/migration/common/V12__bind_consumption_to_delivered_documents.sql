CREATE TABLE document_generation_deliveries (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    wallet_id UUID NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    generated_document_id UUID NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    regeneration BOOLEAN NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_document_generation_delivery_reservation
        FOREIGN KEY (reservation_id) REFERENCES document_credit_reservations(id),
    CONSTRAINT fk_document_generation_delivery_wallet
        FOREIGN KEY (wallet_id) REFERENCES document_credit_wallets(id),
    CONSTRAINT uk_document_generation_delivery_document
        UNIQUE (generated_document_id),
    CONSTRAINT uk_document_generation_delivery_reservation_type
        UNIQUE (reservation_id, document_type),
    CONSTRAINT ck_document_generation_delivery_type
        CHECK (document_type IN ('CV', 'COVER_LETTER'))
);

CREATE INDEX idx_document_generation_delivery_owner_time
    ON document_generation_deliveries(user_id, delivered_at);
