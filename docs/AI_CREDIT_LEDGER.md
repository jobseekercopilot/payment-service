# AI Credit ledger

Payment Service owns each AI Credit wallet, reservation and ledger entry. The
ledger is the auditable source from which a wallet balance must be reconstructable;
the cached balance on `ai_token_wallets` is never authoritative on its own.

## Storage and migrations

Live modes use PostgreSQL 15 through reviewed Flyway migrations:

- `V1__create_ai_credit_ledger.sql` creates wallets, ledger entries,
  reservations, constraints and indexes.
- `V2__make_ledger_entries_append_only.sql` installs the PostgreSQL trigger that
  rejects ledger `UPDATE` and `DELETE` operations with SQL state `55000`.

Hibernate validates the migrated schema and must not create or alter it. H2 and
the common migration are limited to focused automated tests. The `local` profile
uses a separate PostgreSQL database without the live append-only trigger only so
isolated fixture data can be reset.

Every timestamp is an `Instant` stored as `TIMESTAMP WITH TIME ZONE`. Ledger
ordering comes from the database-assigned `sequence_number`; timestamps and UUIDs
must not be used to infer event order.

## Balance rules

Every ledger entry records:

- a non-negative descriptive `token_amount`;
- a signed `balance_delta_tokens`;
- the balance immediately before and after the entry;
- an immutable operation key;
- its wallet, owner, type, references and UTC timestamp.

The database enforces:

```text
balance_after = balance_before + balance_delta_tokens
```

The service additionally requires one continuous chain starting at zero. The sum
of all signed deltas must equal the wallet's stored balance.

| Entry type | Balance effect |
|---|---|
| `FREE_TRIAL_GRANTED` | Positive grant |
| `DEMO_PURCHASE` | Positive isolated-demo grant |
| `PURCHASE` | Positive confirmed purchase |
| `RESERVATION` | Negative hold |
| `SPEND` | Zero when the reservation covers usage; otherwise the negative excess |
| `RESERVATION_RELEASED` | Positive unused/released hold |
| `REFUND` | Positive credit once PAY-11 defines authoritative refund semantics |
| `ADJUSTMENT` | Explicit signed correction with an audited reason |

For example, reserving 10,000 tokens and committing 7,300 creates a -10,000
reservation entry, a zero-delta spend entry describing actual usage, and a +2,700
release entry. The operation keys connect the entries without hiding their
individual balance effects.

Wallet mutation and entry insertion occur in the same Spring transaction.
Database uniqueness on `(wallet_id, operation_id, transaction_type)` prevents
duplicate entries for the same typed operation. PAY-09 retains the broader
concurrency and idempotency work.

## Reconciliation and correction

At application startup, `LedgerStartupVerifier` checks every wallet. Startup
fails if an entry breaks the ordered balance chain or if the signed-delta sum
differs from the stored balance. Production safety checks forbid disabling this
verification.

Never repair an entry with SQL `UPDATE` or `DELETE`. Investigate the originating
operation, preserve the evidence, and append an `ADJUSTMENT` entry with:

1. a unique incident or correction operation key;
2. the signed compensating delta;
3. the exact before/after balance;
4. a non-sensitive reason and incident reference;
5. reviewer approval appropriate to the environment.

The adjustment mechanism is a ledger rule, not a public endpoint. Delivery of
operational adjustment authorization and audit workflow remains separate
controlled work.

## Isolated fixtures

System Data mutation requires all three controls:

- `environment-data.enabled=true`;
- `environment-data.isolated-database=true`;
- an allowed non-production profile.

The production profile is always rejected. Seeded wallets, entries and
reservations must share one owner and wallet, entries must form one chronological
balance chain, and the final entry must reconcile with the wallet. The service
reconciles again inside the seed transaction before accepting the fixture.
