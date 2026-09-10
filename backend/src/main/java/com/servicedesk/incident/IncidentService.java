package com.servicedesk.incident;

import com.servicedesk.common.PagedResponse;
import com.servicedesk.common.exception.BadRequestException;
import com.servicedesk.common.exception.ResourceNotFoundException;
import com.servicedesk.common.exception.UnauthorizedActionException;
import com.servicedesk.incident.dto.*;
import com.servicedesk.kafka.EventPublisher;
import com.servicedesk.kafka.config.KafkaTopicsProperties;
import com.servicedesk.kafka.event.IncidentAssignedEvent;
import com.servicedesk.kafka.event.IncidentCreatedEvent;
import com.servicedesk.kafka.event.IncidentEscalatedEvent;
import com.servicedesk.kafka.event.IncidentResolvedEvent;
import com.servicedesk.notification.NotificationService;
import com.servicedesk.notification.NotificationType;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import com.servicedesk.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository eventRepository;
    private final IncidentCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final IncidentStateMachine stateMachine;
    private final IncidentMapper mapper;
    private final EventPublisher eventPublisher;
    private final KafkaTopicsProperties topics;
    private final NotificationService notificationService;

    private static final int MAX_REMEDIATION_ATTEMPTS = 2;

    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request, User reporter) {
        Incident incident = Incident.builder()
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .severity(request.severity())
                .status(IncidentStatus.OPEN)
                .reporter(reporter)
                .build();
        incident = incidentRepository.save(incident);

        recordEvent(incident, IncidentEventType.CREATED, "Incident reported by " + reporter.getFullName(),
                reporter, null, IncidentStatus.OPEN.name());

        eventPublisher.publish(topics.incidentCreated(), incident.getId().toString(),
                new IncidentCreatedEvent(incident.getId(), incident.getTitle(),
                        incident.getCategory().name(), incident.getSeverity().name(),
                        reporter.getId(), Instant.now()));

        return mapper.toResponse(incident);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(UUID id, User requester) {
        Incident incident = findOrThrow(id);
        enforceViewAccess(incident, requester);
        return mapper.toResponse(incident);
    }

    @Transactional(readOnly = true)
    public PagedResponse<IncidentSummaryResponse> searchIncidents(
            IncidentStatus status, IncidentCategory category, IncidentSeverity severity,
            String keyword, User requester, Pageable pageable) {

        Specification<Incident> spec = Specification.where(IncidentSpecifications.hasStatus(status))
                .and(IncidentSpecifications.hasCategory(category))
                .and(IncidentSpecifications.hasSeverity(severity))
                .and(IncidentSpecifications.keywordMatches(keyword));

        // Employees only ever see their own incidents; engineers/admins see everything.
        if (requester.getRole() == Role.EMPLOYEE) {
            spec = spec.and(IncidentSpecifications.reportedBy(requester.getId()));
        }

        Page<Incident> page = incidentRepository.findAll(spec, pageable);
        return PagedResponse.from(page.map(mapper::toSummary));
    }

    @Transactional
    public IncidentResponse assignIncident(UUID incidentId, AssignIncidentRequest request, User actor) {
        Incident incident = findOrThrow(incidentId);
        User engineer = userRepository.findById(request.engineerId())
                .orElseThrow(() -> new ResourceNotFoundException("Engineer not found: " + request.engineerId()));

        if (engineer.getRole() != Role.ENGINEER && engineer.getRole() != Role.ADMIN) {
            throw new BadRequestException("Incidents can only be assigned to engineers or admins");
        }

        IncidentStatus previous = incident.getStatus();
        if (previous == IncidentStatus.OPEN || previous == IncidentStatus.TRIAGED
                || previous == IncidentStatus.AI_ANALYZED) {
            stateMachine.validateTransition(previous, IncidentStatus.ASSIGNED);
            incident.setStatus(IncidentStatus.ASSIGNED);
        }
        incident.setAssignedEngineer(engineer);
        incidentRepository.save(incident);

        recordEvent(incident, IncidentEventType.ASSIGNED,
                "Assigned to " + engineer.getFullName() + " by " + actor.getFullName(),
                actor, previous.name(), incident.getStatus().name());

        eventPublisher.publish(topics.incidentAssigned(), incident.getId().toString(),
                new IncidentAssignedEvent(incident.getId(), engineer.getId(), actor.getId(), Instant.now()));

        notificationService.notify(engineer, NotificationType.INCIDENT_ASSIGNED,
                "You've been assigned incident \"" + incident.getTitle() + "\" by " + actor.getFullName(),
                incident.getId());

        return mapper.toResponse(incident);
    }

    @Transactional
    public IncidentResponse changeStatus(UUID incidentId, UpdateStatusRequest request, User actor) {
        Incident incident = findOrThrow(incidentId);
        IncidentStatus previous = incident.getStatus();
        stateMachine.validateTransition(previous, request.newStatus());

        incident.setStatus(request.newStatus());
        incidentRepository.save(incident);

        recordEvent(incident, IncidentEventType.STATUS_CHANGED,
                request.reason() != null ? request.reason() : "Status changed by " + actor.getFullName(),
                actor, previous.name(), request.newStatus().name());

        return mapper.toResponse(incident);
    }

    @Transactional
    public IncidentResponse resolveIncident(UUID incidentId, ResolveIncidentRequest request, User actor) {
        Incident incident = findOrThrow(incidentId);
        IncidentStatus previous = incident.getStatus();
        stateMachine.validateTransition(previous, IncidentStatus.RESOLVED);

        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolutionDetails(request.resolutionDetails());
        incident.setResolvedAt(Instant.now());
        incidentRepository.save(incident);

        recordEvent(incident, IncidentEventType.RESOLVED,
                "Resolved by " + actor.getFullName(), actor, previous.name(), IncidentStatus.RESOLVED.name());

        boolean autoRemediated = incident.getRemediationAttempts() > 0;
        eventPublisher.publish(topics.incidentResolved(), incident.getId().toString(),
                new IncidentResolvedEvent(incident.getId(), autoRemediated, Instant.now()));

        notificationService.notify(incident.getReporter(), NotificationType.INCIDENT_RESOLVED,
                "Your incident \"" + incident.getTitle() + "\" has been resolved.", incident.getId());

        return mapper.toResponse(incident);
    }

    @Transactional
    public IncidentResponse escalateIncident(UUID incidentId, EscalateIncidentRequest request, User actor) {
        Incident incident = findOrThrow(incidentId);
        IncidentStatus previous = incident.getStatus();
        stateMachine.validateTransition(previous, IncidentStatus.ESCALATED);

        incident.setStatus(IncidentStatus.ESCALATED);
        incident.setEscalatedAt(Instant.now());
        incident.setEscalationReason(request.reason());
        incidentRepository.save(incident);

        recordEvent(incident, IncidentEventType.ESCALATED, request.reason(),
                actor, previous.name(), IncidentStatus.ESCALATED.name());

        eventPublisher.publish(topics.incidentEscalated(), incident.getId().toString(),
                new IncidentEscalatedEvent(incident.getId(), request.reason(), Instant.now()));

        notifyEscalation(incident);

        return mapper.toResponse(incident);
    }

    private void notifyEscalation(Incident incident) {
        if (incident.getAssignedEngineer() != null) {
            notificationService.notify(incident.getAssignedEngineer(), NotificationType.INCIDENT_ESCALATED,
                    "Incident \"" + incident.getTitle() + "\" has been escalated.", incident.getId());
        }
        notificationService.notify(incident.getReporter(), NotificationType.INCIDENT_ESCALATED,
                "Your incident \"" + incident.getTitle() + "\" has been escalated for further attention.",
                incident.getId());
    }

    @Transactional
    public IncidentResponse reopenIncident(UUID incidentId, User actor) {
        Incident incident = findOrThrow(incidentId);
        IncidentStatus previous = incident.getStatus();
        stateMachine.validateTransition(previous, IncidentStatus.REOPENED);

        incident.setStatus(IncidentStatus.REOPENED);
        incident.setResolvedAt(null);
        incident.setRemediationAttempts(0);
        incidentRepository.save(incident);

        recordEvent(incident, IncidentEventType.REOPENED, "Reopened by " + actor.getFullName(),
                actor, previous.name(), IncidentStatus.REOPENED.name());

        return mapper.toResponse(incident);
    }

    @Transactional
    public CommentResponse addComment(UUID incidentId, AddCommentRequest request, User author) {
        Incident incident = findOrThrow(incidentId);
        enforceViewAccess(incident, author);

        // Employees cannot post internal-only engineer notes.
        boolean internalOnly = author.getRole() != Role.EMPLOYEE && request.internalOnly();

        IncidentComment comment = IncidentComment.builder()
                .incident(incident)
                .author(author)
                .body(request.body())
                .internalOnly(internalOnly)
                .build();
        comment = commentRepository.save(comment);

        recordEvent(incident, IncidentEventType.COMMENT_ADDED,
                author.getFullName() + " commented", author, null, null);

        return mapper.toCommentResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<IncidentEventResponse> getTimeline(UUID incidentId, User requester) {
        Incident incident = findOrThrow(incidentId);
        enforceViewAccess(incident, requester);
        return eventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
                .map(mapper::toEventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(UUID incidentId, User requester) {
        Incident incident = findOrThrow(incidentId);
        enforceViewAccess(incident, requester);
        return commentRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId, org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(c -> requester.getRole() != Role.EMPLOYEE || !c.isInternalOnly())
                .map(mapper::toCommentResponse)
                .toList();
    }

    // --- internal helpers used by other modules (AI analysis, remediation) ---
    // These are intentionally public: the ai/remediation packages depend on
    // incident (not the other way around), so this is the seam they call
    // through rather than reaching into IncidentRepository directly and
    // duplicating status-transition + audit-event logic.

    @Transactional(readOnly = true)
    public Incident getIncidentEntity(UUID id) {
        return findOrThrow(id);
    }

    /**
     * Enforces the same employee-can-only-see-their-own-incident rule as
     * getIncident, for other modules (AI analysis, knowledge recommendations)
     * whose endpoints take an incident ID but don't otherwise go through
     * IncidentService's own read path.
     */
    @Transactional(readOnly = true)
    public void assertCanView(UUID incidentId, User requester) {
        Incident incident = findOrThrow(incidentId);
        enforceViewAccess(incident, requester);
    }

    @Transactional
    public void applyAiAnalysisOutcome(UUID incidentId, String priorityRecommendation, String summary, User actor) {
        Incident incident = findOrThrow(incidentId);
        IncidentStatus previous = incident.getStatus();

        if (stateMachine.canTransition(previous, IncidentStatus.AI_ANALYZED)) {
            incident.setStatus(IncidentStatus.AI_ANALYZED);
        }
        if (priorityRecommendation != null) {
            try {
                incident.setPriority(IncidentPriority.valueOf(priorityRecommendation));
            } catch (IllegalArgumentException ignored) {
                // Unknown priority string from the AI response - leave priority unset
                // rather than fail the whole analysis over a cosmetic field.
            }
        }
        incidentRepository.save(incident);

        recordEvent(incident, IncidentEventType.AI_ANALYSIS_COMPLETED, summary,
                actor, previous.name(), incident.getStatus().name());
    }

    @Transactional
    public void recordAiAnalysisFailure(UUID incidentId, String reason, User actor) {
        Incident incident = findOrThrow(incidentId);
        recordEvent(incident, IncidentEventType.AI_ANALYSIS_FAILED, reason, actor, null, null);
    }

    @Transactional
    public void transitionForRemediation(UUID incidentId, IncidentStatus newStatus, String description, User actor) {
        transitionForRemediation(incidentId, newStatus, description, actor, null);
    }

    @Transactional
    public void transitionForRemediation(UUID incidentId, IncidentStatus newStatus, String description,
                                          User actor, String autoResolutionDetails) {
        Incident incident = findOrThrow(incidentId);
        IncidentStatus previous = incident.getStatus();
        stateMachine.validateTransition(previous, newStatus);
        incident.setStatus(newStatus);
        if (newStatus == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(Instant.now());
            if (autoResolutionDetails != null) {
                incident.setResolutionDetails(autoResolutionDetails);
            }
        }
        if (newStatus == IncidentStatus.ESCALATED) {
            incident.setEscalatedAt(Instant.now());
            incident.setEscalationReason(description);
        }
        incidentRepository.save(incident);
        recordEvent(incident, IncidentEventType.STATUS_CHANGED, description, actor, previous.name(), newStatus.name());

        if (newStatus == IncidentStatus.RESOLVED) {
            eventPublisher.publish(topics.incidentResolved(), incident.getId().toString(),
                    new com.servicedesk.kafka.event.IncidentResolvedEvent(
                            incident.getId(), incident.getRemediationAttempts() > 0, Instant.now()));
            notificationService.notify(incident.getReporter(), NotificationType.INCIDENT_RESOLVED,
                    "Your incident \"" + incident.getTitle() + "\" was automatically resolved.", incident.getId());
        }
        if (newStatus == IncidentStatus.ESCALATED) {
            eventPublisher.publish(topics.incidentEscalated(), incident.getId().toString(),
                    new com.servicedesk.kafka.event.IncidentEscalatedEvent(
                            incident.getId(), description, Instant.now()));
            notifyEscalation(incident);
        }
    }

    @Transactional
    public void recordIncidentEvent(UUID incidentId, IncidentEventType type, String description, User actor) {
        Incident incident = findOrThrow(incidentId);
        recordEvent(incident, type, description, actor, null, null);
    }

    @Transactional
    public void incrementRemediationAttempts(UUID incidentId) {
        Incident incident = findOrThrow(incidentId);
        incident.setRemediationAttempts(incident.getRemediationAttempts() + 1);
        incidentRepository.save(incident);
    }

    Incident findOrThrow(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }

    void enforceViewAccess(Incident incident, User requester) {
        if (requester.getRole() == Role.EMPLOYEE
                && !incident.getReporter().getId().equals(requester.getId())) {
            throw new UnauthorizedActionException("You can only view your own incidents");
        }
    }

    private void recordEvent(Incident incident, IncidentEventType type, String description,
                              User actor, String previousStatus, String newStatus) {
        IncidentEvent event = IncidentEvent.builder()
                .incident(incident)
                .eventType(type)
                .description(description)
                .actor(actor)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .build();
        eventRepository.save(event);
    }

    public int getMaxRemediationAttempts() {
        return MAX_REMEDIATION_ATTEMPTS;
    }
}
