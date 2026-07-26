package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.AiTokenTransaction;
import com.jobseekercopilot.paymentservice.entity.TransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiTokenTransactionRepository extends JpaRepository<AiTokenTransaction, UUID> {
    List<AiTokenTransaction> findByUserIdOrderBySequenceNumberDesc(String userId, Pageable pageable);

    List<AiTokenTransaction> findByUserId(String userId);

    List<AiTokenTransaction> findByWalletIdOrderBySequenceNumberAsc(UUID walletId);

    @Query("""
            select coalesce(sum(transaction.balanceDeltaTokens), 0)
            from AiTokenTransaction transaction
            where transaction.walletId = :walletId
            """)
    long sumBalanceDeltaTokensByWalletId(@Param("walletId") UUID walletId);

    void deleteByUserId(String userId);

    Optional<AiTokenTransaction> findFirstByTransactionTypeAndReferenceTypeAndReferenceId(
            TransactionType transactionType,
            String referenceType,
            String referenceId);

    Optional<AiTokenTransaction> findByWalletIdAndOperationIdAndTransactionType(
            UUID walletId,
            String operationId,
            TransactionType transactionType);
}
