package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.TransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTokenTransactionRepository extends JpaRepository<AiTokenTransaction, UUID> {
    List<AiTokenTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<AiTokenTransaction> findByUserId(String userId);

    void deleteByUserId(String userId);

    Optional<AiTokenTransaction> findFirstByTransactionTypeAndReferenceTypeAndReferenceId(
            TransactionType transactionType,
            String referenceType,
            String referenceId);
}
