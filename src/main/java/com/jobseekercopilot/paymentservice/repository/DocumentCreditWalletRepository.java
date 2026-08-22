package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.DocumentCreditWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentCreditWalletRepository extends JpaRepository<DocumentCreditWallet, UUID> {
    Optional<DocumentCreditWallet> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from DocumentCreditWallet wallet where wallet.userId = :userId")
    Optional<DocumentCreditWallet> findByUserIdForUpdate(@Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from DocumentCreditWallet wallet where wallet.id = :id")
    Optional<DocumentCreditWallet> findByIdForUpdate(@Param("id") UUID id);
}
