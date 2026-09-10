package com.servicedesk.remediation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemediationExecutionRepository extends JpaRepository<RemediationExecution, UUID> {
    List<RemediationExecution> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);
    Optional<RemediationExecution> findByIdempotencyKey(String idempotencyKey);
    long countByStatusAndCreatedAtBetween(RemediationExecutionStatus status, Instant from, Instant to);
}
