# Document-generation allowance

## Customer entitlement

One successful, durable CV delivery consumes one document generation. One
successful, durable cover-letter delivery consumes one. A pair consumes two.
A user-requested regeneration consumes one only when the new document is
durably delivered. Failure before delivery, automatic retry, status polling,
page refresh and idempotent replay consume nothing extra.

The database and Java package retain several historical `document_credit_*`
names during the migration. Their unit is exactly one delivered document, not
provider tokens or inference cost. Payment Gateway is the browser boundary and
exposes only document-generation fields.

## Atomic evidence and reconciliation

The commit transaction locks the reservation and wallet, writes one unique
`document_generation_deliveries` row per delivered document, writes one
append-only `DOCUMENT_SPENT` entry keyed by the generated document UUID, marks
the reservation committed, and updates the wallet. The generated document UUID
is globally unique and the reservation/type pair is unique, so a retry cannot
charge the same delivery again or substitute different evidence.

Startup reconciliation checks the complete wallet chain and each reservation.
For a committed reservation, delivery rows must equal the reserved quantity and
each delivery must have exactly one matching spend. Reserved or released
reservations must have no delivery or spend evidence.

Run the read-only operational report and hard invariant check with an audited
database identity:

```bash
psql "$PAYMENT_DATABASE_URL" \
  --file scripts/report-document-generation-allowance.sql
```

The report answers successful CV/cover-letter deliveries, deliberate
regenerations, consumed/reversed/remaining allowance and the central invariant:
customer consumption events equal chargeable successful deliveries. Provider
model, retry, token and cost telemetry remains in Document Generation Gateway's
restricted internal report and is never copied into this customer ledger.
