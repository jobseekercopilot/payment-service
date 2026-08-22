CREATE OR REPLACE FUNCTION reject_document_credit_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'document credit ledger entries are append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER document_credit_transactions_append_only
BEFORE UPDATE OR DELETE ON document_credit_transactions
FOR EACH ROW EXECUTE FUNCTION reject_document_credit_ledger_mutation();
