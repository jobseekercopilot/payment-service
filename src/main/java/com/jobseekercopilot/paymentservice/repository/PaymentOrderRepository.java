package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.PaymentOrder;
import com.jobseekercopilot.paymentservice.entity.PaymentOrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {
    Optional<PaymentOrder> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
    Optional<PaymentOrder> findByIdAndUserId(UUID id, String userId);
    Optional<PaymentOrder> findByStripeSessionId(String stripeSessionId);
    Optional<PaymentOrder> findByStripePaymentIntentId(String stripePaymentIntentId);
    @Query("select o.id from PaymentOrder o where o.stripeSessionId = :stripeSessionId")
    Optional<UUID> findIdByStripeSessionId(@Param("stripeSessionId") String stripeSessionId);
    @Query("select o.id from PaymentOrder o where o.stripePaymentIntentId = :paymentIntentId")
    Optional<UUID> findIdByStripePaymentIntentId(@Param("paymentIntentId") String paymentIntentId);
    boolean existsByUserIdAndStatusIn(String userId, List<PaymentOrderStatus> statuses);
    List<PaymentOrder> findByUserIdOrderByCreatedAtAsc(String userId);
    List<PaymentOrder> findByUserIdAndStatusIn(String userId, List<PaymentOrderStatus> statuses);

    @Query("select o.id from PaymentOrder o where o.status = :status "
            + "and o.manualReviewReason = :reason "
            + "and o.stripeSessionId is not null order by o.updatedAt asc")
    List<UUID> findProviderSessionExpiryPendingIds(
            @Param("status") PaymentOrderStatus status,
            @Param("reason") String reason,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.id = :id")
    Optional<PaymentOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query("select o.id from PaymentOrder o where o.status in :statuses "
            + "and o.expiresAt <= :expiresAt order by o.expiresAt asc")
    List<UUID> findExpiredIds(
            @Param("statuses") List<PaymentOrderStatus> statuses,
            @Param("expiresAt") Instant expiresAt,
            Pageable pageable);
}
