# Contributing

This is a private, proprietary repository.

1. Start from `develop` and use a focused branch for an approved issue.
2. Keep one issue and one concern per pull request.
3. Never commit credentials, real payment/user data or generated runtime state.
4. Every balance change requires authenticated ownership, durable idempotency,
   concurrency and ledger-invariant tests.
5. Use deterministic fixture/provider modes; automated tests must never make real
   Stripe charges.
6. Run `mvn -B clean verify` and relevant migration/security checks before review.
7. Open a pull request into `develop`; do not push implementation work directly.

Report security concerns using `SECURITY.md`.
