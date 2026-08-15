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
    . as $root |
    (.openapi | type == "string" and startswith("3.")) and
    (.info.version == "3.2.0") and
    (.components.securitySchemes.serviceToken
        | .type == "apiKey" and .in == "header" and .name == "X-Service-Token") and
    (.components.securitySchemes.environmentDataToken
        | .type == "apiKey" and .in == "header" and
          .name == "X-Environment-Data-Token") and
    (.paths["/internal/system-data/seed/payments"].post.security
        == [{"environmentDataToken": []}]) and
    (.paths["/internal/system-data/verify/payments/{userId}"].get.security
        == [{"environmentDataToken": []}]) and
    (.paths["/internal/system-data/scenario/{scenarioId}/payments/{userId}"].delete.security
        == [{"environmentDataToken": []}]) and
    (.paths["/internal/system-data/v1/runtime-owners/{scenarioId}/identities/{identityKey}/owners/{userId}"].delete.security
        == [{"environmentDataToken": []}]) and
    (.paths["/internal/system-data/v1/runtime-owners/{scenarioId}/identities/{identityKey}/owners/{userId}"].get.security
        == [{"environmentDataToken": []}]) and
    (.paths["/api/v1/payments/wallet"].get.operationId == "wallet") and
    (.paths["/api/v1/payments/transactions"].get.operationId == "transactions") and
    (.paths["/api/v1/payments/pricing"].get.operationId == "pricing") and
    (.paths["/api/v1/payments/confirm-stripe-purchase"].post.operationId == "confirmStripePurchase") and
    (.paths["/api/v1/payments/reservations"].post.operationId == "createReservation") and
    (.paths["/api/v1/payments/reservations/{reservationId}"].get.operationId == "reservationStatus") and
    (.paths["/api/v1/payments/reservations/{reservationId}/commit"].post.operationId == "commitReservation") and
    (.paths["/api/v1/payments/reservations/{reservationId}/release"].post.operationId == "releaseReservation") and
    ([
        "/api/v1/payments/wallet",
        "/api/v1/payments/transactions",
        "/api/v1/payments/demo-purchase",
        "/api/v1/payments/estimate",
        "/api/v1/payments/reservations",
        "/api/v1/payments/reservations/{reservationId}",
        "/api/v1/payments/reservations/{reservationId}/commit",
        "/api/v1/payments/reservations/{reservationId}/release",
        "/api/v1/payments/confirm-stripe-purchase"
      ] | all(. as $path |
        ((if $path == "/api/v1/payments/wallet"
              or $path == "/api/v1/payments/transactions"
              or $path == "/api/v1/payments/reservations/{reservationId}"
              then $root.paths[$path].get
              else $root.paths[$path].post end)) as $operation |
        ($operation.security == [{"serviceToken": []}]) and
        ($operation.parameters
          | any(.name == "X-Payment-Owner" and .in == "header" and .required == true))
      )) and
    (.paths["/api/v1/payments/pricing"].get.security == [{"serviceToken": []}]) and
    ((.paths | tostring) | contains("X-User-Id") | not) and
    (.components.schemas.ConfirmStripePurchaseRequest.required
        | index("userId") != null and index("pricingPlanId") != null and index("stripeSessionId") != null) and
    (.components.schemas.CreateReservationRequest.properties.estimatedTokens.minimum == 1) and
    (.components.schemas.CreateReservationRequest.required
        | index("operationKey") != null) and
    (.components.schemas.CreateReservationRequest.properties.operationKey.maxLength == 200) and
    (.components.schemas.CommitReservationRequest.properties.actualTokens.minimum == 0) and
    (.components.schemas.ReservationResponse.properties.status.enum
        == ["RESERVED", "COMMITTED", "RELEASED", "FAILED"]) and
    (.components.schemas.ReservationResponse.properties
        | has("operationKey") and has("expiresAt")) and
    (.components.schemas.ReservationStatusResponse.properties
        | has("operationKey") and has("expiresAt") and has("lastTransitionAt") and
          has("lastTransitionReason") and has("reconciliationAttempts") and
          has("lastReconciliationAttemptAt") and has("reconciliationErrorCode")) and
    (.components.schemas.WalletSummaryResponse.properties
        | has("userId") and has("balanceTokens") and has("lifetimePurchasedTokens") and
          has("lifetimeSpentTokens") and has("lifetimeRefundedTokens") and has("freeTrialGranted")) and
    (.components.schemas.TransactionResponse.properties
        | has("balanceDeltaTokens") and has("operationId")) and
    (.components.schemas.AiTokenTransaction.properties
        | has("sequenceNumber") and has("balanceDeltaTokens") and has("operationId")) and
    (.paths["/api/v2/payments/catalog"].get.operationId == "getDocumentCreditCatalog") and
    (.paths["/api/v2/payments/checkout-readiness"].get.operationId
        == "getDocumentCreditCheckoutReadiness") and
    (.paths["/api/v2/payments/wallet"].get.operationId == "getDocumentCreditWallet") and
    (.paths["/api/v2/payments/transactions"].get.operationId
        == "listDocumentCreditTransactions") and
    (.paths["/api/v2/payments/orders"].post.operationId == "createDocumentCreditOrder") and
    (.paths["/api/v2/payments/orders/{orderId}/status"].get.operationId
        == "getDocumentCreditOrderStatus") and
    (.paths["/api/v2/payments/provider-events/stripe"].post.operationId
        == "reconcileStripeProviderEvent") and
    (.paths["/internal/v2/payments/owners/{owner}/export"].get.operationId
        == "exportPaymentAccountMetadata") and
    (.paths["/internal/v2/payments/owners/{owner}/revoke-access"].post.operationId
        == "revokePaymentAccountAccess") and
    ([
        ["/api/v2/payments/wallet", "get"],
        ["/api/v2/payments/transactions", "get"],
        ["/api/v2/payments/document-credit-reservations", "post"],
        ["/api/v2/payments/document-credit-reservations/{reservationId}", "get"],
        ["/api/v2/payments/document-credit-reservations/{reservationId}/commit", "post"],
        ["/api/v2/payments/document-credit-reservations/{reservationId}/release", "post"],
        ["/api/v2/payments/orders", "post"],
        ["/api/v2/payments/orders/{orderId}", "get"],
        ["/api/v2/payments/orders/{orderId}/status", "get"],
        ["/api/v2/payments/orders/{orderId}/bind-stripe-session", "post"],
        ["/api/v2/payments/orders/{orderId}/cancel", "post"],
        ["/internal/v2/payments/owners/{owner}/export", "get"],
        ["/internal/v2/payments/owners/{owner}/revoke-access", "post"]
      ] | all(. as $route |
        $root.paths[$route[0]][$route[1]] as $operation |
        ($operation.security == [{"serviceToken": []}]) and
        ($operation.parameters
          | any(.name == "X-Payment-Owner" and .in == "header" and .required == true))
      )) and
    (.paths["/api/v2/payments/orders"].post.parameters
        | any(.name == "Idempotency-Key" and .in == "header" and .required == true)) and
    (.paths["/api/v2/payments/catalog"].get.security == [{"serviceToken": []}]) and
    (.paths["/api/v2/payments/checkout-readiness"].get.security
        == [{"serviceToken": []}]) and
    (.paths["/api/v2/payments/provider-events/stripe"].post.security
        == [{"serviceToken": []}]) and
    (.components.schemas.CreatePaymentOrderRequest.required
        | index("pricingPlanId") != null and index("billingCountry") != null and
          index("immediateSupplyRequested") != null and
          index("cancellationRightLossAcknowledged") != null) and
    (.components.schemas.CreatePaymentOrderRequest.properties.immediateSupplyRequested.enum
        == [true]) and
    (.components.schemas.CreatePaymentOrderRequest.properties.cancellationRightLossAcknowledged.enum
        == [true]) and
    (.components.schemas.DocumentCreditCatalogResponse.properties.taxTreatment.enum
        == ["VAT_NOT_CHARGED", "VAT_INCLUDED"]) and
    (.components.schemas.DocumentCreditCatalogResponse.properties.taxStatus.enum
        == ["NOT_CONFIGURED", "NOT_VAT_REGISTERED", "VAT_REGISTERED"]) and
    (.components.schemas.PaymentOrderResponse.properties
        | has("taxStatus") and has("legalEntityType") and
          has("legalEntityConfigurationVersion") and
          has("consumerTermsVersion") and has("consumerTermsAcceptedAt")) and
    (.components.schemas.PaymentOrderStatusResponse.properties
        | has("taxTreatment") and has("taxStatus") and has("legalEntityType") and
          has("legalEntityConfigurationVersion") and has("creditsAdded")) and
    (.components.schemas.AccountPaymentExportResponse.properties
        | has("wallet") and has("transactions") and has("orders") and has("providerEvents")) and
    (.components.schemas.AccountPaymentLifecycleResponse.properties
        | has("providerReconciliationEvidenceRetained") and
          has("checkoutOrdersRevoked") and
          has("providerCheckoutSessionsToExpire") and
          has("providerSessionsRequireExpiry"))
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
