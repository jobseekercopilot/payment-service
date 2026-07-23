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

The captured OpenAPI contract is in `contracts/openapi.json`.

## Licence

Proprietary and confidential. See `LICENSE`.
