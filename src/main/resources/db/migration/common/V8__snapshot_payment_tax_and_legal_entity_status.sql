-- Existing orders cannot be assumed to have had a reviewed tax or seller
-- configuration. Preserve them for reconciliation with an explicit fail-closed
-- snapshot rather than inferring VAT registration or legal identity.
ALTER TABLE payment_orders
    ADD COLUMN tax_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED';

ALTER TABLE payment_orders
    ADD COLUMN legal_entity_type VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED';

ALTER TABLE payment_orders
    ADD COLUMN legal_entity_configuration_version VARCHAR(64)
        NOT NULL DEFAULT 'NOT_CONFIGURED';

ALTER TABLE payment_orders
    ADD CONSTRAINT ck_payment_order_tax_status CHECK (
        tax_status IN ('NOT_CONFIGURED', 'NOT_VAT_REGISTERED', 'VAT_REGISTERED')
    );

ALTER TABLE payment_orders
    ADD CONSTRAINT ck_payment_order_legal_entity_type CHECK (
        legal_entity_type IN ('NOT_CONFIGURED', 'SOLE_TRADER', 'LIMITED_COMPANY')
    );
