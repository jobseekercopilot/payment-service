\set ON_ERROR_STOP on

-- Internal allowance report. Wallet IDs are opaque; do not export user_id.
SELECT delivery.document_type,
       COUNT(*) AS successful_deliveries,
       COUNT(*) FILTER (WHERE delivery.regeneration) AS deliberate_regenerations,
       COUNT(*) FILTER (WHERE NOT delivery.regeneration) AS first_deliveries
  FROM document_generation_deliveries delivery
 GROUP BY delivery.document_type
 ORDER BY delivery.document_type;

SELECT wallet.id AS wallet_id,
       wallet.balance_credits AS remaining_document_generations,
       wallet.lifetime_purchased_credits AS purchased_document_generations,
       wallet.lifetime_spent_credits AS used_document_generations,
       wallet.lifetime_reversed_credits AS reversed_document_generations,
       wallet.review_debt_credits AS review_debt_document_generations,
       wallet.lifecycle_status
  FROM document_credit_wallets wallet
 ORDER BY wallet.created_at, wallet.id;

-- This result must be empty. It is useful for inspection before the hard check below.
WITH delivery_spends AS (
    SELECT delivery.id,
           delivery.reservation_id,
           COUNT(transaction.id) AS spend_count
      FROM document_generation_deliveries delivery
      LEFT JOIN document_credit_transactions transaction
        ON transaction.transaction_type = 'DOCUMENT_SPENT'
       AND transaction.operation_id =
           'DOCUMENT_DELIVERY_COMMIT:' || delivery.generated_document_id::text
     GROUP BY delivery.id, delivery.reservation_id
), reservation_evidence AS (
    SELECT reservation.id,
           reservation.status,
           reservation.document_credits,
           COUNT(delivery.id) AS delivery_count,
           COALESCE(SUM(delivery.spend_count), 0) AS spend_count
      FROM document_credit_reservations reservation
      LEFT JOIN delivery_spends delivery ON delivery.reservation_id = reservation.id
     GROUP BY reservation.id, reservation.status, reservation.document_credits
)
SELECT *
  FROM reservation_evidence
 WHERE (status = 'COMMITTED'
            AND (delivery_count <> document_credits OR spend_count <> delivery_count))
    OR (status <> 'COMMITTED' AND (delivery_count <> 0 OR spend_count <> 0));

-- This result must also be empty: every delivery-style spend must identify one delivery.
SELECT transaction.id,
       transaction.wallet_id,
       transaction.operation_id
  FROM document_credit_transactions transaction
 WHERE transaction.transaction_type = 'DOCUMENT_SPENT'
   AND transaction.operation_id LIKE 'DOCUMENT\_DELIVERY\_COMMIT:%' ESCAPE '\'
   AND NOT EXISTS (
       SELECT 1
         FROM document_generation_deliveries delivery
        WHERE transaction.operation_id =
              'DOCUMENT_DELIVERY_COMMIT:' || delivery.generated_document_id::text
   );

DO $$
DECLARE
    mismatch_count bigint;
    orphan_spend_count bigint;
BEGIN
    WITH delivery_spends AS (
        SELECT delivery.id,
               delivery.reservation_id,
               COUNT(transaction.id) AS spend_count
          FROM document_generation_deliveries delivery
          LEFT JOIN document_credit_transactions transaction
            ON transaction.transaction_type = 'DOCUMENT_SPENT'
           AND transaction.operation_id =
               'DOCUMENT_DELIVERY_COMMIT:' || delivery.generated_document_id::text
         GROUP BY delivery.id, delivery.reservation_id
    ), reservation_evidence AS (
        SELECT reservation.id,
               reservation.status,
               reservation.document_credits,
               COUNT(delivery.id) AS delivery_count,
               COALESCE(SUM(delivery.spend_count), 0) AS spend_count
          FROM document_credit_reservations reservation
          LEFT JOIN delivery_spends delivery ON delivery.reservation_id = reservation.id
         GROUP BY reservation.id, reservation.status, reservation.document_credits
    )
    SELECT COUNT(*) INTO mismatch_count
      FROM reservation_evidence
     WHERE (status = 'COMMITTED'
                AND (delivery_count <> document_credits OR spend_count <> delivery_count))
        OR (status <> 'COMMITTED' AND (delivery_count <> 0 OR spend_count <> 0));

    SELECT COUNT(*) INTO orphan_spend_count
      FROM document_credit_transactions transaction
     WHERE transaction.transaction_type = 'DOCUMENT_SPENT'
       AND transaction.operation_id LIKE 'DOCUMENT\_DELIVERY\_COMMIT:%' ESCAPE '\'
       AND NOT EXISTS (
           SELECT 1
             FROM document_generation_deliveries delivery
            WHERE transaction.operation_id =
                  'DOCUMENT_DELIVERY_COMMIT:' || delivery.generated_document_id::text
       );

    IF mismatch_count <> 0 OR orphan_spend_count <> 0 THEN
        RAISE EXCEPTION
            'Document-generation delivery/consumption reconciliation failed: % mismatches, % orphan spends',
            mismatch_count, orphan_spend_count;
    END IF;
END $$;
