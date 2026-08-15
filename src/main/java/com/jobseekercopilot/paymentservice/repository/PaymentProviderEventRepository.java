package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.PaymentProviderEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderEventRepository extends JpaRepository<PaymentProviderEvent, UUID> {
    Optional<PaymentProviderEvent> findByProviderAndProviderEventId(String provider, String providerEventId);
    List<PaymentProviderEvent> findByOrderIdInOrderByReceivedAtAsc(List<UUID> orderIds);
    List<PaymentProviderEvent> findByOrderIdAndOutcomeOrderByReceivedAtAsc(
            UUID orderId, String outcome);
    List<PaymentProviderEvent>
            findByProviderAndProviderObjectIdAndOutcomeOrderByReceivedAtAsc(
                    String provider, String providerObjectId, String outcome);
}
