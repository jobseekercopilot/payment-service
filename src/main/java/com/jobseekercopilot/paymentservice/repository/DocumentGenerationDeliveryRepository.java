package com.jobseekercopilot.paymentservice.repository;

import com.jobseekercopilot.paymentservice.entity.DocumentGenerationDelivery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentGenerationDeliveryRepository
        extends JpaRepository<DocumentGenerationDelivery, UUID> {
    List<DocumentGenerationDelivery> findByReservationIdOrderByDocumentType(
            UUID reservationId);

    boolean existsByGeneratedDocumentId(UUID generatedDocumentId);
}
