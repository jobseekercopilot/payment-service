# Payment Service identity boundary

Every `/api/v1/payments` request requires exactly one `X-Service-Token`.
Tokens contain at least 32 UTF-8 bytes, are distinct per caller, and are
compared in constant time. A valid token identifies one of four callers:

| Caller | Allowed operations |
| --- | --- |
| Payment Gateway | wallet, transactions, pricing, estimate and demo purchase |
| Document Generation Gateway | create, inspect, commit and release owner-scoped reservations |
| CV and Cover Letter Service | create, commit and release reservations |
| Stripe Gateway | confirm a signed Stripe purchase |

All owner-scoped operations also require exactly one `X-Payment-Owner`.
Controllers use only the filter-bound owner attribute. `X-User-Id` is rejected,
and a Stripe confirmation body whose user ID differs from the authenticated
owner is rejected before a wallet is created or credited.

Reservation reads use the combined reservation ID and owner predicate. A
missing reservation and a reservation belonging to another owner therefore
produce the same response.

## Configuration

- `PAYMENT_GATEWAY_TO_PAYMENT_SERVICE_TOKEN`
- `DOCUMENT_GENERATION_GATEWAY_TO_PAYMENT_SERVICE_TOKEN`
- `CV_COVER_LETTER_TO_PAYMENT_SERVICE_TOKEN`
- `STRIPE_GATEWAY_TO_PAYMENT_SERVICE_TOKEN`

Startup fails when a token is absent, shorter than 32 bytes or duplicates
another caller token. Rotate a token with the single authorized caller and
Payment Service together. Never reuse browser credentials or provider secrets
as service tokens.

The Stripe webhook remains authenticated at Stripe Gateway by its provider
signature. Payment Service trusts a fulfilment command only when the Stripe
Gateway token and the owner recovered from signed checkout metadata are both
present and agree with the request body.
