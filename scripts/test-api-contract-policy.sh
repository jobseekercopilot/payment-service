#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT

copy_contract() {
    local destination="$1"
    mkdir -p "$destination"
    cp "$repository_root/contracts/openapi.json" "$repository_root/contracts/SHA256SUMS" "$destination/"
}

"$repository_root/scripts/verify-api-contract.sh" "$repository_root/contracts/openapi.json" >/dev/null

copy_contract "$temporary_dir/checksum-drift"
jq '.info.description = "unreviewed drift"' \
    "$temporary_dir/checksum-drift/openapi.json" \
    > "$temporary_dir/checksum-drift/changed.json"
mv "$temporary_dir/checksum-drift/changed.json" "$temporary_dir/checksum-drift/openapi.json"
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/checksum-drift/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted checksum drift" >&2
    exit 1
fi

copy_contract "$temporary_dir/reservation-operation"
jq 'del(.paths["/api/v1/payments/reservations/{reservationId}/commit"])' \
    "$temporary_dir/reservation-operation/openapi.json" \
    > "$temporary_dir/reservation-operation/changed.json"
mv "$temporary_dir/reservation-operation/changed.json" "$temporary_dir/reservation-operation/openapi.json"
(cd "$temporary_dir/reservation-operation" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/reservation-operation/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted removal of reservation commit" >&2
    exit 1
fi

copy_contract "$temporary_dir/fulfilment-key"
jq '.components.schemas.ConfirmStripePurchaseRequest.required -= ["stripeSessionId"]' \
    "$temporary_dir/fulfilment-key/openapi.json" \
    > "$temporary_dir/fulfilment-key/changed.json"
mv "$temporary_dir/fulfilment-key/changed.json" "$temporary_dir/fulfilment-key/openapi.json"
(cd "$temporary_dir/fulfilment-key" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/fulfilment-key/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted removal of the current fulfilment key" >&2
    exit 1
fi

copy_contract "$temporary_dir/token-bound"
jq '.components.schemas.CreateReservationRequest.properties.estimatedTokens.minimum = 0' \
    "$temporary_dir/token-bound/openapi.json" \
    > "$temporary_dir/token-bound/changed.json"
mv "$temporary_dir/token-bound/changed.json" "$temporary_dir/token-bound/openapi.json"
(cd "$temporary_dir/token-bound" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/token-bound/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted an invalid reservation bound" >&2
    exit 1
fi

copy_contract "$temporary_dir/reservation-operation-key"
jq '.components.schemas.CreateReservationRequest.required -= ["operationKey"]' \
    "$temporary_dir/reservation-operation-key/openapi.json" \
    > "$temporary_dir/reservation-operation-key/changed.json"
mv "$temporary_dir/reservation-operation-key/changed.json" \
    "$temporary_dir/reservation-operation-key/openapi.json"
(cd "$temporary_dir/reservation-operation-key" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" \
        "$temporary_dir/reservation-operation-key/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted removal of reservation operation key" >&2
    exit 1
fi

copy_contract "$temporary_dir/reservation-recovery"
jq 'del(.paths["/api/v1/payments/reservations/{reservationId}"])' \
    "$temporary_dir/reservation-recovery/openapi.json" \
    > "$temporary_dir/reservation-recovery/changed.json"
mv "$temporary_dir/reservation-recovery/changed.json" \
    "$temporary_dir/reservation-recovery/openapi.json"
(cd "$temporary_dir/reservation-recovery" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" \
        "$temporary_dir/reservation-recovery/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted removal of reservation recovery lookup" >&2
    exit 1
fi

copy_contract "$temporary_dir/owner-boundary"
jq 'del(.paths["/api/v1/payments/wallet"].get.parameters)' \
    "$temporary_dir/owner-boundary/openapi.json" \
    > "$temporary_dir/owner-boundary/changed.json"
mv "$temporary_dir/owner-boundary/changed.json" "$temporary_dir/owner-boundary/openapi.json"
(cd "$temporary_dir/owner-boundary" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/owner-boundary/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted removal of trusted payment-owner context" >&2
    exit 1
fi

copy_contract "$temporary_dir/service-identity"
jq 'del(.components.securitySchemes.serviceToken)' \
    "$temporary_dir/service-identity/openapi.json" \
    > "$temporary_dir/service-identity/changed.json"
mv "$temporary_dir/service-identity/changed.json" "$temporary_dir/service-identity/openapi.json"
(cd "$temporary_dir/service-identity" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/service-identity/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted removal of service authentication" >&2
    exit 1
fi

copy_contract "$temporary_dir/ledger-delta"
jq 'del(.components.schemas.TransactionResponse.properties.balanceDeltaTokens)' \
    "$temporary_dir/ledger-delta/openapi.json" \
    > "$temporary_dir/ledger-delta/changed.json"
mv "$temporary_dir/ledger-delta/changed.json" "$temporary_dir/ledger-delta/openapi.json"
(cd "$temporary_dir/ledger-delta" && sha256sum openapi.json > SHA256SUMS)
if "$repository_root/scripts/verify-api-contract.sh" "$temporary_dir/ledger-delta/openapi.json" >/dev/null 2>&1; then
    echo "API contract negative test accepted removal of the signed ledger delta" >&2
    exit 1
fi

echo "API contract policy negative tests passed"
