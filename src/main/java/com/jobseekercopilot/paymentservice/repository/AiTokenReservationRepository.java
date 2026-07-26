package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.AiTokenReservation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiTokenReservationRepository extends JpaRepository<AiTokenReservation, UUID> {
    List<AiTokenReservation> findByUserId(String userId);
    Optional<AiTokenReservation> findByIdAndUserId(UUID id, String userId);
    Optional<AiTokenReservation> findByUserIdAndOperationKey(String userId, String operationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation
            from AiTokenReservation reservation
            where reservation.id = :id and reservation.userId = :userId
            """)
    Optional<AiTokenReservation> findByIdAndUserIdForUpdate(
            @Param("id") UUID id,
            @Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation
            from AiTokenReservation reservation
            where reservation.id = :id
            """)
    Optional<AiTokenReservation> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select reservation.id
            from AiTokenReservation reservation
            where reservation.status = :status and reservation.expiresAt <= :expiresAt
            order by reservation.expiresAt, reservation.id
            """)
    List<UUID> findExpiredIds(
            @Param("status") com.jobseekercopilot.paymentservice.entity.ReservationStatus status,
            @Param("expiresAt") Instant expiresAt,
            Pageable pageable);

    void deleteByUserId(String userId);
}
