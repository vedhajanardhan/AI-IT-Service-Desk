package com.servicedesk.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, UUID> {
    List<IncidentEvent> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
