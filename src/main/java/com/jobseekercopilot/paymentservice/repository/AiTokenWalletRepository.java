package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.AiTokenWallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTokenWalletRepository extends JpaRepository<AiTokenWallet, UUID> {
    Optional<AiTokenWallet> findByUserId(String userId);
    void deleteByUserId(String userId);
}
