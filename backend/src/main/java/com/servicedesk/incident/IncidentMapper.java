package com.servicedesk.incident;

import com.servicedesk.incident.dto.*;
import org.springframework.stereotype.Component;

@Component
public class IncidentMapper {

    public IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getCategory(),
                incident.getSeverity(),
                incident.getPriority(),
                incident.getStatus(),
                incident.getReporter().getId(),
                incident.getReporter().getFullName(),
                incident.getAssignedEngineer() != null ? incident.getAssignedEngineer().getId() : null,
                incident.getAssignedEngineer() != null ? incident.getAssignedEngineer().getFullName() : null,
                incident.getResolutionDetails(),
                incident.getRemediationAttempts(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                incident.getResolvedAt(),
                incident.getEscalatedAt(),
                incident.getEscalationReason()
        );
    }

    public IncidentSummaryResponse toSummary(Incident incident) {
        return new IncidentSummaryResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getCategory(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getAssignedEngineer() != null ? incident.getAssignedEngineer().getFullName() : null,
                incident.getCreatedAt()
        );
    }

    public CommentResponse toCommentResponse(IncidentComment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getAuthor().getFullName(),
                comment.getBody(),
                comment.isInternalOnly(),
                comment.getCreatedAt()
        );
    }

    public IncidentEventResponse toEventResponse(IncidentEvent event) {
        return new IncidentEventResponse(
                event.getId(),
                event.getEventType(),
                event.getDescription(),
                event.getActor() != null ? event.getActor().getFullName() : "system",
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getCreatedAt()
        );
    }
}
