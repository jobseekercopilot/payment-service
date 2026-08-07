CREATE OR REPLACE FUNCTION reject_ai_token_ledger_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    environment_reset_owner TEXT :=
        current_setting('jobseekercopilot.environment_data_reset_owner', TRUE);
BEGIN
    IF TG_OP = 'DELETE'
        AND environment_reset_owner IS NOT NULL
        AND environment_reset_owner <> ''
        AND environment_reset_owner = OLD.user_id THEN
        RETURN OLD;
    END IF;

    RAISE EXCEPTION 'AI Credit ledger entries are append-only'
        USING ERRCODE = '55000';
END;
$$;
