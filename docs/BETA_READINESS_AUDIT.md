# Payment Service beta-readiness audit

Audit date: 2026-07-23  
Decision: **Not ready for private beta**

This is an audit baseline only. It does not authorize real charges or represent a
financial ledger ready for production use.

## Verified behavior

- Creates one token wallet per trusted payment-owner context and grants starter
  tokens.
- Lists configured GBP pricing plans and a limited recent transaction history.
- Supports demo purchase, Stripe purchase confirmation, estimation and
  reserve/commit/release workflows.
- Uses explicit ledger transaction boundaries and bounds the transaction-history
  limit to 1–100.
- `mvn -B --no-transfer-progress clean verify` passed 45 tests with no
  failures/errors/skips, including PostgreSQL migration and recovery evidence.

## Critical findings

### Trusted service and owner boundary delivered

PAY-03 requires route-authorized service identities and a trusted
`X-Payment-Owner` context. The caller-controlled legacy `X-User-Id` header is
rejected, and Stripe confirmation requires the Stripe Gateway service identity.
The payment route remains disabled while the remaining provider and ledger
controls are delivered.

### Durable ledger foundation delivered; environment recovery remains

PAY-08 replaces the runtime H2 default with PostgreSQL 15, reviewed Flyway
migrations, explicit constraints, a live append-only trigger, signed deltas,
database-assigned entry order, UTC timestamps and fail-closed startup
reconciliation. Automated PostgreSQL tests prove migrations, persistence,
invariants, append-only enforcement and dump/restore reconstruction. Deployment
database provisioning, scheduled encrypted backups, retention and environment
restore rehearsals still require platform delivery before beta.

### Wallet and reservation concurrency controls delivered

PAY-09 adds pessimistic wallet/reservation row locks, optimistic versions,
race-safe atomic starter-wallet provisioning, exact arithmetic and retry-safe,
mutually exclusive commit/release transitions. Real PostgreSQL stress tests
prove one starter grant under 12 simultaneous first-use requests, no reservation
overspend, no lost concurrent credits, one terminal transition and reconciled
wallet/ledger state.

Stripe idempotency still performs a find-then-insert with no globally unique
provider fulfillment invariant. Different wallets can race the same provider
session, so PAY-07 remains a P0 beta blocker for real payment traffic.

### Remaining ledger lifecycle work

Signed reservation, spend and release entries now state their balance effects and
reconstruct the wallet, with immutable operation keys and startup reconciliation.
Expiry, stuck-reservation cleanup, ambiguous consumer outcomes and the failed CV
release path remain in PAY-10 and related workflow issues.

### Untrusted Stripe confirmation

Confirmation checks the current plan's token count but not an owned checkout/order,
paid status, currency, amount, environment/livemode, event identity or pricing
snapshot. Plan changes can reject legitimate older sessions, while direct requests
can attempt to grant credit.

### Unsafe non-production controls

The browser-facing demo purchase still mutates balances without a mode guard.
System Data mutation is now restricted to explicitly enabled, isolated,
non-production databases, and payment fixtures must form a reconciled ledger.
Broader service authentication and demo-mode controls remain required.

### Privacy and licensing

Logs contain raw users, balances, reservation/transaction/Stripe session IDs and
free-text reasons. The OpenAPI metadata says MIT while the repository is
proprietary; this mismatch must be corrected before publication/use.

## Functional classification

| Capability | Result |
|---|---|
| Wallet/starter grant | Concurrency-safe ledger foundation delivered |
| Pricing | Incomplete; snapshot/disclosure rules absent |
| Purchase confirmation | Unsafe |
| Reservation lifecycle | Concurrency-safe; expiry/compensation outstanding |
| Transaction history | Signed/reconcilable; cursor pagination outstanding |
| Refund/chargeback | Model placeholders only; behavior absent |
| Durable storage/migrations | Code foundation delivered; environment recovery evidence outstanding |
| Observability/operations | Basic logs/health only |

Missing tests include authoritative concurrent provider fulfillment,
refund/dispute, expiry/compensation, privacy redaction and full browser/provider
journeys.

The Payments epic contains the remaining focused beta-readiness issues.
