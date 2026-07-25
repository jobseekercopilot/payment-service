#!/usr/bin/env bash
set -euo pipefail

contract="${1:-contracts/openapi.json}"
generated="${2:-}"
contract_dir="$(dirname "$contract")"
manifest="$contract_dir/SHA256SUMS"

for required_file in "$contract" "$manifest"; do
    if [[ ! -f "$required_file" || -L "$required_file" ]]; then
        echo "API contract policy: required regular file is missing or is a symlink: $required_file" >&2
        exit 1
    fi
done

(
    cd "$contract_dir"
    sha256sum --check --strict SHA256SUMS
)

jq -e '
    (.openapi | type == "string" and startswith("3.")) and
    (.info.version == "1.0.0") and
    (.paths["/api/v1/payments/wallet"].get.operationId == "wallet") and
    (.paths["/api/v1/payments/transactions"].get.operationId == "transactions") and
    (.paths["/api/v1/payments/pricing"].get.operationId == "pricing") and
    (.paths["/api/v1/payments/confirm-stripe-purchase"].post.operationId == "confirmStripePurchase") and
    (.paths["/api/v1/payments/reservations"].post.operationId == "createReservation") and
    (.paths["/api/v1/payments/reservations/{reservationId}/commit"].post.operationId == "commitReservation") and
    (.paths["/api/v1/payments/reservations/{reservationId}/release"].post.operationId == "releaseReservation") and
    (.components.schemas.ConfirmStripePurchaseRequest.required
        | index("userId") != null and index("pricingPlanId") != null and index("stripeSessionId") != null) and
    (.components.schemas.CreateReservationRequest.properties.estimatedTokens.minimum == 1) and
    (.components.schemas.CommitReservationRequest.properties.actualTokens.minimum == 0) and
    (.components.schemas.ReservationResponse.properties.status.enum
        == ["RESERVED", "COMMITTED", "RELEASED", "FAILED"]) and
    (.components.schemas.WalletSummaryResponse.properties
        | has("userId") and has("balanceTokens") and has("lifetimePurchasedTokens") and
          has("lifetimeSpentTokens") and has("lifetimeRefundedTokens") and has("freeTrialGranted"))
' "$contract" >/dev/null

if [[ -n "$generated" ]]; then
    if [[ ! -f "$generated" || -L "$generated" ]]; then
        echo "API contract policy: generated contract is missing or is a symlink: $generated" >&2
        exit 1
    fi
    temporary_dir="$(mktemp -d)"
    trap 'rm -rf "$temporary_dir"' EXIT
    jq -S . "$contract" > "$temporary_dir/reviewed.json"
    jq -S . "$generated" > "$temporary_dir/generated.json"
    if ! cmp -s "$temporary_dir/reviewed.json" "$temporary_dir/generated.json"; then
        echo "API contract policy: generated OpenAPI differs from contracts/openapi.json" >&2
        diff -u "$temporary_dir/reviewed.json" "$temporary_dir/generated.json" >&2 || true
        exit 1
    fi
fi

echo "API contract policy: reviewed Payment Service contract is intact and compatible"
