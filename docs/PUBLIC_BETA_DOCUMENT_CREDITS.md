# Public-beta document credits and payment operations

This is the release and incident runbook for the public-beta commercial model.
It supersedes AI-token commercial assumptions in the historical readiness
audit. It does not authorise a deployment or real charges.

## Server-owned offer

The catalog version is `public-beta-2026-08-22`, the currency is GBP and the
billing country is GB. A new wallet receives two free document credits once.

| Pack | Gross consumer total | Credits | Founding bonus |
|---|---:|---:|---:|
| Starter | £4.99 | 10 | 5 |
| Active | £11.99 | 25 | 13 |
| Power | £19.99 | 60 | 30 |

One successfully stored CV uses one credit. One successfully stored cover
letter uses one credit. A paired legacy request reserves and commits two. A
regeneration is detected from prior durable delivery for the saved job,
identified as a regeneration and charged in exactly the same way. Model-token
estimates and actual usage are provider telemetry/cost data only; they never
change document-credit consumption. A deterministic no-charge fallback is
stored and its hold is released.

Existing AI-token value migrates once with `ceil(balance_tokens / 10000)`.
The migrated-token figure is retained only as migration evidence. New runtime
spend never consults token usage or the 10,000-unit conversion.

Displayed prices are fixed gross consumer totals. `taxTreatment` is either
`VAT_NOT_CHARGED` or `VAT_INCLUDED`; `taxStatus` is `NOT_CONFIGURED`,
`NOT_VAT_REGISTERED` or `VAT_REGISTERED`. `VAT_INCLUDED` is valid only with
`VAT_REGISTERED`; both other statuses require `VAT_NOT_CHARGED`. The current
operator statement is not VAT registered, so the three displayed prices remain
the final consumer totals with no VAT component and must not be described as
including VAT. `NOT_CONFIGURED` always blocks Checkout.

The seller configuration is typed as `NOT_CONFIGURED`, `SOLE_TRADER` or
`LIMITED_COMPANY`. A reviewed, versioned configuration is required for
Checkout, but no placeholder trader or company identity is hard-coded. Tax
status, tax treatment, seller type and seller-configuration version are
snapshotted on each order for later receipt and reconciliation evidence.

## Release gates

Production refuses to start when a required commercial setting is implicit.
Before an authorised test deployment, configure all of:

- `PAYMENT_CHECKOUT_ENABLED`
- `PAYMENT_CHECKOUT_RELEASE_AUTHORISED`
- `PAYMENT_PROVIDER_LIVE_MODE_EXPECTED`
- `PAYMENT_TAX_TREATMENT`
- `PAYMENT_TAX_STATUS`
- `PAYMENT_LEGAL_ENTITY_TYPE`
- `PAYMENT_LEGAL_ENTITY_CONFIGURATION_VERSION`
- `PAYMENT_LEGAL_ENTITY_REVIEWED`
- `PAYMENT_FOUNDING_PROMOTION_ENABLED`
- `PAYMENT_FOUNDING_PROMOTION_RELEASE_AUTHORISED`
- `PAYMENT_CATALOG_VERSION`
- `PAYMENT_CONSUMER_TERMS_VERSION`
- `PAYMENT_FINANCIAL_RECORD_RETENTION_YEARS`
- `PAYMENT_STRIPE_LIFECYCLE_GATEWAY_URL`
- `PAYMENT_SERVICE_TO_STRIPE_GATEWAY_LIFECYCLE_TOKEN`
- `PAYMENT_PROVIDER_SESSION_RECOVERY_ENABLED`

Keep Checkout and both release-authorisation flags false, the promotion
disabled, and Stripe in `DISABLED` mode until the release decision. Enabling
Checkout without release authorisation fails startup. Enabling the promotion
without both payment gates also fails startup. Demo purchase and legacy Stripe
confirmation are forbidden in production.

A production service may be staged with the legal and tax values explicitly
`NOT_CONFIGURED` only while Checkout remains disabled. An authorised Checkout
release fails startup unless tax status is configured and the versioned seller
configuration has been reviewed.

`/actuator/health` is the process/database health boundary. The authenticated
browser capability boundary is `GET /api/v2/payments/checkout-readiness` on
Payment Gateway. It remains `checkoutAvailable=false` until Payment Service and
Stripe Gateway both report `READY`. A catalog never advertises an enabled
founding offer when Payment Service Checkout is unavailable.

## Checkout and fulfilment trust boundary

Payment Service creates a durable, owner-scoped order from the catalog. It
requires true `immediateSupplyRequested` and
`cancellationRightLossAcknowledged` values, then records the server-owned terms
version and acceptance timestamp before Stripe session creation. Price,
currency, credits, tax and seller snapshots, return URLs and expiry are never
accepted from the browser.

Stripe Checkout must collect a billing address and accept cards only. A signed
`checkout.session.completed` event is authoritative. Fulfilment requires the
owned order/session, paid and complete states, exact GBP amount, expected
provider mode, a payment-intent ID and the settled customer billing country GB.
Any mismatch remains unfulfilled in `MANUAL_REVIEW`.

Success and cancel URLs contain only `order_id=<UUID>`. They never prove payment.
The owner-scoped order-status endpoint is the only return-page source. Only
`FULFILLED` with `creditsAdded=true` may say that credits were added.

## Founding promotion

The first 200 distinct paying owners may receive a 50% credit bonus on their
first completed purchase. Active reservations plus completed claims can never
exceed 200. Checkout creation atomically reserves a slot under a database lock;
the response guarantees the exact bonus through its order `expiresAt`.

Completion converts the reservation once. Cancellation, provider expiry and
the scheduled bounded recovery of expired open orders release it once. A
refund does not reopen a completed founding slot. Catalog `AVAILABLE` is an
indication only; only `promotionGuaranteed=true` on an owned Checkout response
is a promise.

## Refunds, disputes and reconciliation

Verified `charge.refunded` events reverse credits proportionally, rounded down
in the customer's favour.
A full refund reverses all base and promotional credits.
`charge.dispute.created` reverses all credits. Duplicate provider event IDs
replay without mutation; a reused ID with different evidence is rejected.

A refund/dispute event arriving before Checkout completion is retained as
unmatched evidence. Completion links it by owned order or payment intent and
applies it in the same database transaction, preventing a net grant. Provider
mode, currency and payment-intent mismatches move the order to manual review
without changing credit balance.

If already-spent credits cannot all be removed, the wallet records review debt,
becomes `BLOCKED_REVIEW`, and no further generation or fulfilment is allowed.
Operations must inspect the order, signed provider event, wallet chain and
Stripe dashboard; never edit or delete a ledger row. Dispute closure and any
approved reinstatement require a separately authorised append-only adjustment
procedure until an automated signed closure-event workflow is released.

## Recovery and account lifecycle

Every document hold and Checkout/promotion reservation has a bounded expiry.
The scheduled reconciler selects a bounded batch and performs each transition
in its own transaction under reservation/order then wallet/campaign locks.
Recovery is idempotent and does not depend on a Stripe expiry webhook.

Account export and revocation are internal, owner-scoped operations authorised
only by the account-lifecycle service token. Export contains financial metadata
and content-free provider identifiers, but never card data, addresses, secrets
or generated document content. Revocation blocks wallet/Checkout access,
releases document holds and cancels open orders. Payment Service then expires
each owned Stripe Checkout session through a dedicated Payment-to-Stripe
lifecycle identity. Authentication Service never receives provider credentials
or calls Stripe directly. A provider outage returns retryable
`503 PROVIDER_SESSION_EXPIRY_PENDING`; the bounded Payment Service scheduler
keeps retrying, and a later revocation retry succeeds only after every session
is confirmed expired. It does not delete ledger, order or
provider-reconciliation evidence.

Financial records are configured for seven years as a conservative provisional
period. Before live release the operator must provide reviewed sole-trader or
company identity details, approve the consumer-terms text/version, confirm the
current `NOT_VAT_REGISTERED` treatment with a UK accountant/legal adviser,
document retention start/end events and decide when owner identifiers may be
pseudonymised or erased. Until then, evidence is retained and no destructive
payment-record deletion endpoint is exposed.

## Release and incident checklist

1. Back up the database and rehearse restore with the document ledger chain.
2. Apply and review Flyway migrations against a production-shaped clone.
3. Start with Checkout/promotion/LIVE flags disabled and confirm health.
4. Confirm legal/tax values and publish the matching terms version.
5. Configure a pinned Stripe API version, restricted live key and signing
   secret; register the webhook with the same version.
6. Run a Stripe test-mode Starter purchase, GB mismatch, expiry, full refund,
   early-refund replay, duplicate webhook and account-deletion session expiry.
7. Reconcile Stripe totals, orders, provider events and document-ledger balances
   to zero unexplained differences.
8. Obtain the release decision, then change Payment and Stripe release gates in
   one reviewed rollout.
9. Monitor unmatched/manual-review events, expired holds, blocked wallets,
   founding counts and webhook failures.

If reconciliation fails, disable new Checkout while keeping webhooks and
owner-status reads available, preserve all evidence, and investigate before
granting or removing credits.
