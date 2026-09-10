package com.servicedesk.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.ai.dto.AiAnalysisResponse;
import com.servicedesk.ai.dto.AiAnalysisResult;
import com.servicedesk.ai.provider.AiProvider;
import com.servicedesk.ai.provider.AiProviderFactory;
import com.servicedesk.common.exception.AiProviderException;
import com.servicedesk.incident.*;
import com.servicedesk.kafka.EventPublisher;
import com.servicedesk.kafka.config.KafkaTopicsProperties;
import com.servicedesk.notification.NotificationService;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the spec's "AI analysis" test flow, with particular attention to
 * the requirement that "AI failure must NOT break the normal
 * incident-management workflow" - the failure-path test asserts the
 * incident's status is left untouched and no exception escapes the service.
 */
@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock private AiAnalysisRepository aiAnalysisRepository;
    @Mock private AiProviderFactory providerFactory;
    @Mock private AiProvider provider;
    @Mock private IncidentService incidentService;
    @Mock private EventPublisher eventPublisher;
    @Mock private KafkaTopicsProperties topics;
    @Mock private NotificationService notificationService;

    private AiAnalysisService aiAnalysisService;

    private User engineer;
    private Incident incident;

    @BeforeEach
    void setUp() {
        aiAnalysisService = new AiAnalysisService(
                aiAnalysisRepository,
                providerFactory,
                incidentService,
                eventPublisher,
                topics,
                notificationService);

        engineer = User.builder()
                .role(Role.ENGINEER)
                .fullName("Evan Engineer")
                .build();
        engineer.setId(UUID.randomUUID());

        incident = Incident.builder()
                .title("Checkout API 503s")
                .description("Intermittent 503s under load")
                .category(IncidentCategory.APPLICATION_ERROR)
                .severity(IncidentSeverity.HIGH)
                .status(IncidentStatus.TRIAGED)
                .reporter(engineer)
                .assignedEngineer(engineer)
                .build();
        incident.setId(UUID.randomUUID());
    }

    @Test
    void analyzeIncident_persistsSuccessAndUpdatesIncident() {
        when(providerFactory.getActiveProvider()).thenReturn(provider);
        when(incidentService.getIncidentEntity(incident.getId())).thenReturn(incident);
        when(aiAnalysisRepository.save(any())).thenAnswer(inv -> {
            AiAnalysis a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        when(provider.providerName()).thenReturn("mock");
        when(provider.analyze(any())).thenReturn(new AiAnalysisResult(
                "APPLICATION_ERROR",
                "HIGH",
                "P2_HIGH",
                "Downstream timeout",
                0.75,
                "RESTART_APPLICATION_SERVICE",
                "Looks like a transient dependency issue",
                List.of("http-500-503-errors")));

        AiAnalysisResponse response =
                aiAnalysisService.analyzeIncident(incident.getId(), engineer);

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.recommendedActionCode())
                .isEqualTo("RESTART_APPLICATION_SERVICE");

        verify(incidentService).applyAiAnalysisOutcome(
                eq(incident.getId()),
                eq("P2_HIGH"),
                anyString(),
                eq(engineer));

        verify(notificationService).notify(
                eq(engineer),
                eq(com.servicedesk.notification.NotificationType.AI_ANALYSIS_COMPLETED),
                anyString(),
                eq(incident.getId()));
    }

    @Test
    void analyzeIncident_providerFailureIsCaughtAndDoesNotBreakTheWorkflow() {
        when(providerFactory.getActiveProvider()).thenReturn(provider);
        when(incidentService.getIncidentEntity(incident.getId())).thenReturn(incident);
        when(aiAnalysisRepository.save(any())).thenAnswer(inv -> {
            AiAnalysis a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        when(provider.providerName()).thenReturn("mock");
        when(provider.analyze(any()))
                .thenThrow(new AiProviderException("Timed out"));

        AiAnalysisResponse response =
                aiAnalysisService.analyzeIncident(incident.getId(), engineer);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).isEqualTo("Timed out");

        // The incident's own status transition is never touched on failure -
        // applyAiAnalysisOutcome (which changes status) must not be called.
        verify(incidentService, never())
                .applyAiAnalysisOutcome(any(), any(), any(), any());

        verify(incidentService).recordAiAnalysisFailure(
                eq(incident.getId()),
                anyString(),
                eq(engineer));
    }

    @Test
    void analyzeIncident_rejectsEmployeeTryingToTriggerAnalysis() {
        User employee = User.builder()
                .role(Role.EMPLOYEE)
                .fullName("Riya Reporter")
                .build();
        employee.setId(UUID.randomUUID());

        assertThatThrownBy(() ->
                aiAnalysisService.analyzeIncident(incident.getId(), employee))
                .isInstanceOf(
                        com.servicedesk.common.exception.UnauthorizedActionException.class);

        verifyNoInteractions(provider);
    }

    @Test
    void getAnalysisHistory_rejectsEmployeeViewingSomeoneElsesIncident() {
        User otherEmployee = User.builder()
                .role(Role.EMPLOYEE)
                .fullName("Olu Other")
                .build();
        otherEmployee.setId(UUID.randomUUID());

        doThrow(new com.servicedesk.common.exception.UnauthorizedActionException("not yours"))
                .when(incidentService)
                .assertCanView(incident.getId(), otherEmployee);

        assertThatThrownBy(() ->
                aiAnalysisService.getAnalysisHistory(incident.getId(), otherEmployee))
                .isInstanceOf(
                        com.servicedesk.common.exception.UnauthorizedActionException.class);

        verifyNoInteractions(aiAnalysisRepository);
    }
}