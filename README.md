# Payment Service

## Role in Job Seeker Copilot

| Role | Called by | Calls | Data | Local port |
|---|---|---|---|---:|
| System of record for document-credit wallets, append-only transactions, reservations and payment orders | Payment/Stripe gateways, Document Generation, CV/Cover Letter, Account Lifecycle | Stripe Gateway (Checkout-session lifecycle only) | Own PostgreSQL database | 8099 |

See the central [payment journey/status](https://docs.jobseekercopilot.com/journeys/reporting-payments/), [payment domain model](https://docs.jobseekercopilot.com/data/domain-models/), and [data ownership](https://docs.jobseekercopilot.com/data/ownership/).

Spring Boot service for the Job Seeker Copilot document-generation allowance,
transaction, reservation and owned payment-order model. Historical
`documentCredit` field names remain internal/API compatibility terms. The
inherited AI-token ledger remains available for migration and backward
compatibility only.

See [`docs/PUBLIC_BETA_DOCUMENT_CREDITS.md`](docs/PUBLIC_BETA_DOCUMENT_CREDITS.md)
for the current public-beta commercial contract, release gates, reconciliation
and account-lifecycle runbook. The older
[`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md) is retained as a
historical audit snapshot.

## Build

Java 17 and Maven are required.

```bash
mvn -B clean verify
```

Production requires PostgreSQL 15, verified TLS, reviewed Flyway migrations and
startup reconciliation. H2 is test-only. Wallet and reservation concurrency are
database-controlled; abandoned holds expire and are reconciled with durable
evidence. Stripe LIVE mode and Checkout remain fail-closed until explicit,
coordinated release configuration is supplied.

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

Contract version 3.4.0 requires every delivered CV or cover letter to be bound
to its exact generation-consumption record. It preserves the existing v2 field
and route names for compatibility while making delivery—not provider token
usage—the only consumption boundary. Existing v1 Payment and fixture operations
remain compatible but cannot mutate commercial value in production through
demo or legacy Stripe confirmation routes.

The successful-delivery rule, migration meaning and executable allowance
reconciliation report are documented in
[`docs/DOCUMENT_GENERATION_ALLOWANCE.md`](docs/DOCUMENT_GENERATION_ALLOWANCE.md).

The full test suite needs a Docker-compatible runtime because it proves the
ledger against PostgreSQL 15, including dump/restore and append-only enforcement.

All payment API operations are service-authenticated and route-authorized.
Owner-scoped operations use only the trusted `X-Payment-Owner` context; the
legacy caller-controlled `X-User-Id` header is rejected. See
[`docs/PAYMENT_IDENTITY_BOUNDARY.md`](docs/PAYMENT_IDENTITY_BOUNDARY.md) for the
caller matrix, required secrets, owner predicate and rotation constraints.

## Licence

Proprietary and confidential. See `LICENSE`.
