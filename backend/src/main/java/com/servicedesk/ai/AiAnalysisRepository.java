package com.servicedesk.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, UUID> {

    List<AiAnalysis> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);

    Optional<AiAnalysis> findFirstByIncidentIdAndStatusOrderByCreatedAtDesc(UUID incidentId, AiAnalysisStatus status);

    long countByStatusAndCreatedAtBetween(AiAnalysisStatus status, Instant from, Instant to);
}
