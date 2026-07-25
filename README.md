# Payment Service

Spring Boot service for the inherited Job Seeker Copilot AI Credit wallet,
transaction and reservation model.

This repository is a sanitised audit baseline, not a beta-ready ledger or payment
system. See [`docs/BETA_READINESS_AUDIT.md`](docs/BETA_READINESS_AUDIT.md).

## Build

Java 17 and Maven are required.

```bash
mvn -B clean verify
```

The inherited source builds locally, but its H2 storage, authentication, ledger
concurrency, Stripe confirmation and operational controls are not suitable for
payment traffic.

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

## Licence

Proprietary and confidential. See `LICENSE`.
