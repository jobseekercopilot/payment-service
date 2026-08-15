-- Provider events can arrive concurrently at different service instances. A
-- dedicated row lock serializes the initial replay lookup and append so the
-- unique provider/event constraint remains a final invariant rather than an
-- application-visible 500 response.
CREATE TABLE payment_provider_event_ingest_locks (
    id INTEGER PRIMARY KEY
);

INSERT INTO payment_provider_event_ingest_locks (id) VALUES (1);
