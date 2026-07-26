# Payment Service contract governance

Payment Service owns `contracts/openapi.json` for wallet, pricing, ledger,
fulfilment and reservation operations. Consumers may pin or generate from the
reviewed producer bytes, but must not export a running instance and treat that
as the source of truth.

Version 2.0 establishes the breaking trusted-identity boundary: each operation
requires `serviceToken`, and each owner-scoped operation requires
`X-Payment-Owner`. The retired `X-User-Id` contract must not be reintroduced.
PAY-08 adds signed `balanceDeltaTokens` and immutable `operationId` fields to
transaction responses. The isolated System Data transaction schema also exposes
the database-assigned `sequenceNumber`; seed validation, not caller input,
determines the stored order.

`contracts/SHA256SUMS` protects the reviewed contract.
`scripts/verify-api-contract.sh` enforces the current compatibility boundary
and, after Maven verification, compares the source contract byte-for-structure
with `target/openapi.json`. Negative policy tests prove that checksum drift,
operation removal and required value-bound changes fail closed.

Contract changes follow additive semantic versioning. A breaking change needs
a coordinated major-version migration. Consumers record the exact merged
producer revision, source path and SHA-256. No generated source or binary is
committed as a workaround.

Rollback restores the prior service implementation and matching reviewed
contract. The signed ledger fields are additive, but removing or changing their
meaning requires coordinated consumer migration. Provider idempotency and the
remaining payment lifecycle semantics remain governed by their dedicated PAY
issues.
