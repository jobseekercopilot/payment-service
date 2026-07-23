# Payment Service beta-readiness audit

Audit date: 2026-07-23  
Decision: **Not ready for private beta**

This is an audit baseline only. It does not authorize real charges or represent a
financial ledger ready for production use.

## Verified behavior

- Creates one token wallet per supplied user ID and grants starter tokens.
- Lists configured GBP pricing plans and a limited recent transaction history.
- Supports demo purchase, Stripe purchase confirmation, estimation and
  reserve/commit/release workflows.
- Uses transactions around service methods and bounds the transaction-history
  limit to 1–100.
- `mvn -q clean verify` passed 12 tests with no failures/errors/skips.

## Critical findings

### Unauthenticated balance ownership

Browser/service-supplied `X-User-Id` values control wallet, ledger and reservation
scope. `confirm-stripe-purchase` accepts a JSON `userId` and grants tokens without
authentication or service authorization. Reservation lookup checks equality only
against another supplied user ID.

### Non-durable database

The default database is in-memory H2 with an enabled console, `ddl-auto=update` and
SQL logging. No migration, durable encrypted database, backup/restore or retention
baseline exists.

### Concurrent double credit/spend

Wallet rows have no optimistic version or locking strategy. Starter-wallet
creation, reservation, commit/release and purchase updates can race. Stripe
idempotency performs a find-then-insert with no unique database constraint on the
checkout-session reference, so concurrent webhook delivery can double-credit.

### Ledger semantics and reconciliation

Reservations deduct the balance, while the later visible SPEND transaction records
equal before/after balances. The client hides reservation/release entries, so the
displayed ledger cannot independently reconcile balance movement. No immutable
ledger invariant, operation key, expiry, stuck-reservation cleanup or reconciliation
job exists. A failed release in CV generation is swallowed and can leave credit
reserved.

### Untrusted Stripe confirmation

Confirmation checks the current plan's token count but not an owned checkout/order,
paid status, currency, amount, environment/livemode, event identity or pricing
snapshot. Plan changes can reject legitimate older sessions, while direct requests
can attempt to grant credit.

### Unsafe non-production controls

The browser-facing demo purchase mutates balances without a mode guard. Internal
System Data endpoints can seed arbitrary entity graphs when enabled; their default
allowed environments include `default` and no service authentication is present.

### Privacy and licensing

Logs contain raw users, balances, reservation/transaction/Stripe session IDs and
free-text reasons. The OpenAPI metadata says MIT while the repository is
proprietary; this mismatch must be corrected before publication/use.

## Functional classification

| Capability | Result |
|---|---|
| Wallet/starter grant | Incomplete; race and identity risks |
| Pricing | Incomplete; snapshot/disclosure rules absent |
| Purchase confirmation | Unsafe |
| Reservation lifecycle | Incomplete; concurrency/expiry/reconciliation absent |
| Transaction history | Incomplete; no cursor and misleading visible ledger |
| Refund/chargeback | Model placeholders only; behavior absent |
| Durable storage/migrations | Absent but required |
| Observability/operations | Basic logs/health only |

Missing tests include validated authentication, cross-user access, concurrent
purchase/reserve/commit/release, unique idempotency, refund/dispute, expiry,
reconciliation, database migration, privacy redaction and full browser/provider
journeys.

The Payments epic contains focused follow-up issues. All remain Backlog and no
issue was implemented during this audit.
