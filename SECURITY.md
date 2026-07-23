# Security policy

This repository is not ready to own private-beta balances or payment confirmations.

Report suspected vulnerabilities privately to the repository owner. Do not put
Stripe identifiers, user identifiers, balances, ledger entries, transaction
references, secrets or exploit details in ordinary issues or logs.

The audit found unauthenticated caller-controlled identity and internal confirmation
paths, mutable demo/system-data endpoints, non-durable storage and concurrency gaps.
Keep payment capability disabled at the client boundary until these controls and
cross-user tests are complete.

Rotate any exposed credential in the relevant system/provider. Removing a Git
commit does not revoke it.
