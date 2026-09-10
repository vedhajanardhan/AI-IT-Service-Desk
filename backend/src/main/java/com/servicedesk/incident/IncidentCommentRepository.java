package com.servicedesk.incident;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncidentCommentRepository extends JpaRepository<IncidentComment, UUID> {
    Page<IncidentComment> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId, Pageable pageable);
}
