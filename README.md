# Payment Service

Spring Boot service for the inherited Job Seeker Copilot AI Credit wallet,
transaction and reservation model.

This repository contains the migrated PostgreSQL AI Credit ledger foundation. The
larger payment system is not yet beta-ready. See
[`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).

## Build

Java 17 and Maven are required.

```bash
mvn -B clean verify
```

Production requires PostgreSQL 15, verified TLS, reviewed Flyway migrations and
startup reconciliation. H2 is test-only. Wallet and reservation concurrency are
database-controlled; abandoned holds expire and are reconciled with durable
evidence. Stripe fulfillment and the remaining operational controls are still
not suitable for payment traffic.

See [`docs/AI_CREDIT_LEDGER.md`](docs/AI_CREDIT_LEDGER.md) for the schema,
signed-entry semantics and correction rules. See
[`docs/DATABASE_OPERATIONS.md`](docs/DATABASE_OPERATIONS.md) for the database,
migration, backup and restore baseline.
See [`docs/RESERVATION_RECOVERY.md`](docs/RESERVATION_RECOVERY.md) for operation
keys, expiry, state transitions, consumer retries and the recovery runbook.

The producer-owned OpenAPI contract is in `contracts/openapi.json`. Its
checksum, compatibility policy and generated-contract equality are verified by:

```bash
./scripts/test-api-contract-policy.sh
./scripts/verify-api-contract.sh
mvn -B --no-transfer-progress clean verify
./scripts/verify-api-contract.sh contracts/openapi.json target/openapi.json
```

See [`docs/CONTRACT_GOVERNANCE.md`](docs/CONTRACT_GOVERNANCE.md) for ownership,
versioning and consumer-pin rules.

The full test suite needs a Docker-compatible runtime because it proves the
ledger against PostgreSQL 15, including dump/restore and append-only enforcement.

All payment API operations are service-authenticated and route-authorized.
Owner-scoped operations use only the trusted `X-Payment-Owner` context; the
legacy caller-controlled `X-User-Id` header is rejected. See
[`docs/PAYMENT_IDENTITY_BOUNDARY.md`](docs/PAYMENT_IDENTITY_BOUNDARY.md) for the
caller matrix, required secrets, owner predicate and rotation constraints.

## Licence

Proprietary and confidential. See `LICENSE`.
