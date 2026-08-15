package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.PaymentProviderEventIngestLock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentProviderEventIngestLockRepository
        extends JpaRepository<PaymentProviderEventIngestLock, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from PaymentProviderEventIngestLock l where l.id = :id")
    Optional<PaymentProviderEventIngestLock> findByIdForUpdate(@Param("id") Integer id);
}
