package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.FoundingPromotionReservation;
import com.jobseekercopilot.paymentservice.entity.PromotionReservationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoundingPromotionReservationRepository
        extends JpaRepository<FoundingPromotionReservation, UUID> {
    Optional<FoundingPromotionReservation> findByOrderId(UUID orderId);
    Optional<FoundingPromotionReservation> findFirstByUserIdAndStatusIn(
            String userId, List<PromotionReservationStatus> statuses);
    boolean existsByCompletedOwnerKey(String completedOwnerKey);
}
