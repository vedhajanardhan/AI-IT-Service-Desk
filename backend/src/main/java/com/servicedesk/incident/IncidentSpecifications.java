package com.servicedesk.incident;

import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * Composable JPA Specifications backing incident search/filter/sort so the
 * controller can combine any subset of filters without N hand-written
 * repository query methods.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {}

    public static Specification<Incident> hasStatus(IncidentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Incident> hasCategory(IncidentCategory category) {
        return (root, query, cb) -> category == null ? null : cb.equal(root.get("category"), category);
    }

    public static Specification<Incident> hasSeverity(IncidentSeverity severity) {
        return (root, query, cb) -> severity == null ? null : cb.equal(root.get("severity"), severity);
    }

    public static Specification<Incident> reportedBy(UUID reporterId) {
        return (root, query, cb) -> reporterId == null ? null : cb.equal(root.get("reporter").get("id"), reporterId);
    }

    public static Specification<Incident> assignedTo(UUID engineerId) {
        return (root, query, cb) -> engineerId == null ? null : cb.equal(root.get("assignedEngineer").get("id"), engineerId);
    }

    public static Specification<Incident> keywordMatches(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String like = "%" + keyword.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like)
            );
        };
    }
}
