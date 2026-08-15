package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransaction;
import com.jobseekercopilot.paymentservice.entity.DocumentCreditTransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentCreditTransactionRepository
        extends JpaRepository<DocumentCreditTransaction, UUID> {
    List<DocumentCreditTransaction> findByUserIdOrderBySequenceNumberDesc(
            String userId, Pageable pageable);
    List<DocumentCreditTransaction> findByUserIdOrderBySequenceNumberAsc(String userId);
    List<DocumentCreditTransaction> findByWalletIdOrderBySequenceNumberAsc(UUID walletId);
    Optional<DocumentCreditTransaction> findByWalletIdAndOperationIdAndTransactionType(
            UUID walletId, String operationId, DocumentCreditTransactionType transactionType);

    @Query("select coalesce(sum(t.balanceDeltaCredits), 0) from DocumentCreditTransaction t where t.walletId = :walletId")
    long sumBalanceDeltaByWalletId(@Param("walletId") UUID walletId);
}
