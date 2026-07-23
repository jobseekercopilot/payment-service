package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.AiTokenReservation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTokenReservationRepository extends JpaRepository<AiTokenReservation, UUID> {
    List<AiTokenReservation> findByUserId(String userId);
    void deleteByUserId(String userId);
}
