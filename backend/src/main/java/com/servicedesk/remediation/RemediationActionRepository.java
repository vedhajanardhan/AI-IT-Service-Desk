package com.servicedesk.remediation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemediationActionRepository extends JpaRepository<RemediationAction, UUID> {
    Optional<RemediationAction> findByCode(String code);
    List<RemediationAction> findByEnabledTrue();
}
