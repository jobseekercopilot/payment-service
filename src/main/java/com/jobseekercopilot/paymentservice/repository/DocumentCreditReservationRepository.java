package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservation;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditReservationStatus;
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

public interface DocumentCreditReservationRepository
        extends JpaRepository<DocumentCreditReservation, UUID> {
    Optional<DocumentCreditReservation> findByUserIdAndOperationKey(String userId, String operationKey);
    Optional<DocumentCreditReservation> findByIdAndUserId(UUID id, String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DocumentCreditReservation r where r.id = :id and r.userId = :userId")
    Optional<DocumentCreditReservation> findByIdAndUserIdForUpdate(
            @Param("id") UUID id, @Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DocumentCreditReservation r where r.id = :id")
    Optional<DocumentCreditReservation> findByIdForUpdate(@Param("id") UUID id);

    @Query("select r.id from DocumentCreditReservation r "
            + "where r.status = :status and r.expiresAt <= :expiresAt order by r.expiresAt asc")
    List<UUID> findExpiredIds(
            @Param("status") DocumentCreditReservationStatus status,
            @Param("expiresAt") Instant expiresAt,
            Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DocumentCreditReservation r "
            + "where r.userId = :userId and r.status = :status "
            + "order by r.createdAt asc, r.id asc")
    List<DocumentCreditReservation> findByUserIdAndStatusForUpdate(
            @Param("userId") String userId,
            @Param("status") DocumentCreditReservationStatus status);

    List<DocumentCreditReservation> findByUserIdOrderByCreatedAtAsc(String userId);
    List<DocumentCreditReservation> findByWalletId(UUID walletId);
}
