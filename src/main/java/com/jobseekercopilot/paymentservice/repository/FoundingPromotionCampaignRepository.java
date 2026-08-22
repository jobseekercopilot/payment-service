package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.FoundingPromotionCampaign;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoundingPromotionCampaignRepository
        extends JpaRepository<FoundingPromotionCampaign, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FoundingPromotionCampaign c where c.id = :id")
    Optional<FoundingPromotionCampaign> findByIdForUpdate(@Param("id") String id);
}
