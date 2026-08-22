# Reservation recovery

## State machine

`RESERVED` is created with an owner-scoped operation key and a finite
`expiresAt`. A caller may commit it to `COMMITTED` or release it to `RELEASED`.
The expiry reconciler may also move an expired `RESERVED` hold to `RELEASED`.
The reservation row is locked before its wallet in every terminal transition,
so commit, release and expiry cannot all win.

Duplicate create calls replay the original creation ledger balance only when
the feature, estimate and reference fields match. Duplicate commits must carry
the same actual token amount. Duplicate releases replay the recorded release.
Conflicting terminal transitions fail closed.

## Timeouts and consumer contract

Consumers create one unpredictable operation key per logical Payment reserve
attempt and retain it for every retry of that attempt. The default hold time is
15 minutes. A lost create response may therefore be retried without creating a
second hold. Commit and release are retry-safe; consumers may use
`GET /api/v1/payments/reservations/{reservationId}` with the same authenticated
owner to resolve an ambiguous terminal response.

An operation key does not make a complete document-generation request
idempotent. A separately initiated generation must use a new key.

## Reconciliation and operations

The scheduler scans a bounded batch every 30 seconds by default. Each release
runs in its own transaction. A failed recovery rolls back the ledger mutation,
leaves the hold `RESERVED` for retry, and records a separate durable attempt
timestamp and redacted error code. Successful recovery records
`RELEASED_AFTER_EXPIRY` and clears the error code.

Production safety rejects disabled recovery or non-positive TTL, interval or
batch settings. Operators can inspect owner-scoped lifecycle evidence and
correlate it with the immutable `RESERVATION_RELEASE:<reservationId>` ledger
entry. A repeatedly failing hold must be investigated before any manual ledger
adjustment; ledger rows remain append-only.

Configuration:

- `PAYMENT_RESERVATION_TTL` (default `PT15M`)
- `PAYMENT_RESERVATION_RECOVERY_INTERVAL` (default `PT30S`)
- `PAYMENT_RESERVATION_RECOVERY_BATCH_SIZE` (default `100`)
- `PAYMENT_RESERVATION_RECOVERY_ENABLED` (must remain `true` outside isolated tests)
