CREATE OR REPLACE FUNCTION reject_ai_token_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'AI Credit ledger entries are append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER ai_token_transactions_append_only
BEFORE UPDATE OR DELETE ON ai_token_transactions
FOR EACH ROW
EXECUTE FUNCTION reject_ai_token_ledger_mutation();
