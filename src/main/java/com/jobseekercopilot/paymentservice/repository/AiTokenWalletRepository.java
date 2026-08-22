package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiTokenWalletRepository extends JpaRepository<AiTokenWallet, UUID> {
    Optional<AiTokenWallet> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from AiTokenWallet wallet where wallet.userId = :userId")
    Optional<AiTokenWallet> findByUserIdForUpdate(@Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from AiTokenWallet wallet where wallet.id = :walletId")
    Optional<AiTokenWallet> findByIdForUpdate(@Param("walletId") UUID walletId);

    void deleteByUserId(String userId);
}
