ALTER TABLE document_credit_wallets
    DROP CONSTRAINT ck_document_credit_wallet_lifecycle;

ALTER TABLE document_credit_wallets
    ADD CONSTRAINT ck_document_credit_wallet_lifecycle CHECK (
        lifecycle_status IN (
            'ACTIVE', 'BLOCKED_REVIEW', 'REVOCATION_PENDING', 'REVOKED'
        )
    );
