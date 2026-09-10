package com.servicedesk.ai;

import com.servicedesk.ai.dto.AiAnalysisRequest;
import com.servicedesk.ai.dto.AiAnalysisResponse;
import com.servicedesk.ai.dto.AiAnalysisResult;
import com.servicedesk.ai.provider.AiProvider;
import com.servicedesk.ai.provider.AiProviderFactory;
import com.servicedesk.common.exception.AiProviderException;
import com.servicedesk.common.exception.ResourceNotFoundException;
import com.servicedesk.common.exception.UnauthorizedActionException;
import com.servicedesk.incident.Incident;
import com.servicedesk.incident.IncidentService;
import com.servicedesk.kafka.EventPublisher;
import com.servicedesk.kafka.config.KafkaTopicsProperties;
import com.servicedesk.kafka.event.IncidentAnalyzedEvent;
import com.servicedesk.notification.NotificationService;
import com.servicedesk.notification.NotificationType;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiProviderFactory providerFactory;
    private final IncidentService incidentService;
    private final EventPublisher eventPublisher;
    private final KafkaTopicsProperties topics;
    private final NotificationService notificationService;

    /**
     * Runs AI analysis for an incident. Deliberately swallows provider
     * failures into a FAILED analysis record rather than letting them
     * propagate - per spec, "AI failure must NOT break the normal
     * incident-management workflow." The incident simply stays wherever
     * it was; a human can still triage/assign/resolve it manually.
     */
    @Transactional
    public AiAnalysisResponse analyzeIncident(UUID incidentId, User actor) {
        if (actor.getRole() == Role.EMPLOYEE) {
            throw new UnauthorizedActionException("Only engineers or admins can trigger AI analysis");
        }
        Incident incident = incidentService.getIncidentEntity(incidentId);
        AiProvider provider = providerFactory.getActiveProvider();

        AiAnalysisRequest request = new AiAnalysisRequest(
                incident.getId(), incident.getTitle(), incident.getDescription(),
                incident.getCategory().name(), incident.getSeverity().name());

        try {
            AiAnalysisResult result = provider.analyze(request);

            AiAnalysis analysis = AiAnalysis.builder()
                    .incident(incident)
                    .status(AiAnalysisStatus.SUCCEEDED)
                    .provider(provider.providerName())
                    .classification(result.classification())
                    .severityRecommendation(result.severityRecommendation())
                    .priorityRecommendation(result.priorityRecommendation())
                    .rootCause(result.rootCause())
                    .confidenceScore(result.confidenceScore())
                    .recommendedActionCode(result.recommendedActionCode())
                    .explanation(result.explanation())
                    .relevantKnowledgeTags(String.join(",", result.relevantKnowledgeTags()))
                    .build();
            analysis = aiAnalysisRepository.save(analysis);

            incidentService.applyAiAnalysisOutcome(incidentId, result.priorityRecommendation(),
                    "AI analysis completed (" + provider.providerName() + ", confidence "
                            + String.format("%.0f%%", result.confidenceScore() * 100) + "): " + result.explanation(),
                    actor);

            eventPublisher.publish(topics.incidentAnalyzed(), incidentId.toString(),
                    new IncidentAnalyzedEvent(incidentId, result.rootCause(), result.confidenceScore(), Instant.now()));

            User notifyTarget = incident.getAssignedEngineer() != null ? incident.getAssignedEngineer() : incident.getReporter();
            notificationService.notify(notifyTarget, NotificationType.AI_ANALYSIS_COMPLETED,
                    "AI analysis completed for \"" + incident.getTitle() + "\" (" + result.classification() + ", "
                            + String.format("%.0f%%", result.confidenceScore() * 100) + " confidence).",
                    incidentId);

            return toResponse(analysis);

        } catch (AiProviderException e) {
            log.warn("AI analysis failed for incident {}: {}", incidentId, e.getMessage());

            AiAnalysis failed = AiAnalysis.builder()
                    .incident(incident)
                    .status(AiAnalysisStatus.FAILED)
                    .provider(provider.providerName())
                    .failureReason(e.getMessage())
                    .build();
            failed = aiAnalysisRepository.save(failed);

            incidentService.recordAiAnalysisFailure(incidentId,
                    "AI analysis failed (" + provider.providerName() + "): " + e.getMessage(), actor);

            return toResponse(failed);
        }
    }

    @Transactional(readOnly = true)
    public List<AiAnalysisResponse> getAnalysisHistory(UUID incidentId, User requester) {
        incidentService.assertCanView(incidentId, requester);
        return aiAnalysisRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiAnalysisResponse getLatestSuccessfulAnalysis(UUID incidentId, User requester) {
        incidentService.assertCanView(incidentId, requester);
        AiAnalysis analysis = aiAnalysisRepository
                .findFirstByIncidentIdAndStatusOrderByCreatedAtDesc(incidentId, AiAnalysisStatus.SUCCEEDED)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No successful AI analysis exists yet for incident: " + incidentId));
        return toResponse(analysis);
    }

    private AiAnalysisResponse toResponse(AiAnalysis a) {
        return new AiAnalysisResponse(
                a.getId(), a.getIncident().getId(), a.getClassification(), a.getSeverityRecommendation(),
                a.getPriorityRecommendation(), a.getRootCause(),
                a.getConfidenceScore() != null ? a.getConfidenceScore() : 0.0,
                a.getRecommendedActionCode(), a.getExplanation(),
                a.getRelevantKnowledgeTags() != null && !a.getRelevantKnowledgeTags().isBlank()
                        ? List.of(a.getRelevantKnowledgeTags().split(",")) : List.of(),
                a.getStatus().name(), a.getFailureReason(), a.getCreatedAt());
    }
}
