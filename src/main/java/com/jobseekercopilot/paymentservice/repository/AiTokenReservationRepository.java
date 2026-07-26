package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.AiTokenReservation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiTokenReservationRepository extends JpaRepository<AiTokenReservation, UUID> {
    List<AiTokenReservation> findByUserId(String userId);
    Optional<AiTokenReservation> findByIdAndUserId(UUID id, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation
            from AiTokenReservation reservation
            where reservation.id = :id and reservation.userId = :userId
            """)
    Optional<AiTokenReservation> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") String userId);

    void deleteByUserId(String userId);
}
