package com.servicedesk.remediation;

import com.servicedesk.common.exception.BadRequestException;
import com.servicedesk.incident.Incident;
import com.servicedesk.incident.IncidentCategory;
import com.servicedesk.incident.IncidentSeverity;
import com.servicedesk.incident.IncidentService;
import com.servicedesk.incident.IncidentStatus;
import com.servicedesk.kafka.EventPublisher;
import com.servicedesk.kafka.config.KafkaTopicsProperties;
import com.servicedesk.notification.NotificationService;
import com.servicedesk.remediation.dto.RejectRemediationRequest;
import com.servicedesk.remediation.dto.RequestRemediationRequest;
import com.servicedesk.remediation.executor.RemediationExecutionOutcome;
import com.servicedesk.remediation.executor.RemediationExecutor;
import com.servicedesk.remediation.executor.RemediationExecutorFactory;
import com.servicedesk.remediation.health.HealthChecker;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the spec's remediation-related required test flows: remediation
 * recommendation, approval, successful remediation, failed remediation,
 * retry, and escalation. The executor and health checker are mocked here
 * (not MockRemediationExecutor/MockHealthChecker) so each test controls
 * the exact outcome instead of relying on their randomized simulation.
 */
@ExtendWith(MockitoExtension.class)
class RemediationServiceTest {

    @Mock
    private RemediationActionRepository actionRepository;

    @Mock
    private RemediationExecutionRepository executionRepository;

    @Mock
    private RemediationExecutorFactory executorFactory;

    @Mock
    private RemediationExecutor executor;

    @Mock
    private HealthChecker healthChecker;

    @Mock
    private IncidentService incidentService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private KafkaTopicsProperties topics;

    @Mock
    private NotificationService notificationService;

    private RemediationService remediationService;

    private User reporter;
    private User engineer;

    private Incident incident;

    private RemediationAction approvalRequiredAction;
    private RemediationAction autoApprovedAction;

    @BeforeEach
    void setUp() {

        remediationService = new RemediationService(
                actionRepository,
                executionRepository,
                new RemediationPolicyValidator(),
                executorFactory,
                healthChecker,
                incidentService,
                eventPublisher,
                topics,
                notificationService
        );

        /*
         * Keep reporter and assigned engineer as different users.
         *
         * RemediationService intentionally notifies BOTH users after a
         * successful remediation.
         */
        reporter = User.builder()
                .role(Role.EMPLOYEE)
                .fullName("Riya Reporter")
                .build();
        reporter.setId(UUID.randomUUID());

        engineer = User.builder()
                .role(Role.ENGINEER)
                .fullName("Evan Engineer")
                .build();
        engineer.setId(UUID.randomUUID());

        incident = Incident.builder()
                .title("Redis cache misses spiking")
                .description("cache miss rate way up")
                .category(IncidentCategory.INFRASTRUCTURE)
                .severity(IncidentSeverity.HIGH)
                .status(IncidentStatus.AI_ANALYZED)
                .reporter(reporter)
                .assignedEngineer(engineer)
                .remediationAttempts(0)
                .build();

        incident.setId(UUID.randomUUID());

        approvalRequiredAction = RemediationAction.builder()
                .code("RESTART_CONNECTION_POOL")
                .name("Restart Connection Pool")
                .description("desc")
                .riskLevel(RiskLevel.MEDIUM)
                .requiredRole(Role.ENGINEER)
                .approvalRequired(true)
                .timeoutSeconds(10)
                .retryLimit(2)
                .enabled(true)
                .build();

        approvalRequiredAction.setId(UUID.randomUUID());

        autoApprovedAction = RemediationAction.builder()
                .code("INVALIDATE_REDIS_CACHE")
                .name("Invalidate Redis Cache")
                .description("desc")
                .riskLevel(RiskLevel.LOW)
                .requiredRole(Role.ENGINEER)
                .approvalRequired(false)
                .timeoutSeconds(10)
                .retryLimit(2)
                .enabled(true)
                .build();

        autoApprovedAction.setId(UUID.randomUUID());
    }

    @Test
    void requestRemediation_withApprovalRequiredAction_createsPendingApprovalAndNotifiesEngineer() {

        when(incidentService.getIncidentEntity(incident.getId()))
                .thenReturn(incident);

        when(actionRepository.findByCode("RESTART_CONNECTION_POOL"))
                .thenReturn(Optional.of(approvalRequiredAction));

        when(executionRepository.save(any()))
                .thenAnswer(inv -> {
                    RemediationExecution execution = inv.getArgument(0);

                    if (execution.getId() == null) {
                        execution.setId(UUID.randomUUID());
                    }

                    return execution;
                });

        var response = remediationService.requestRemediation(
                incident.getId(),
                new RequestRemediationRequest("RESTART_CONNECTION_POOL"),
                engineer
        );

        assertThat(response.status())
                .isEqualTo("PENDING_APPROVAL");

        verify(notificationService)
                .notify(
                        eq(engineer),
                        eq(com.servicedesk.notification.NotificationType.REMEDIATION_APPROVAL_REQUIRED),
                        anyString(),
                        eq(incident.getId())
                );

        verify(eventPublisher, never())
                .publish(any(), any(), any());
    }

    @Test
    void requestRemediation_withAutoApprovedAction_dispatchesImmediately() {

        when(incidentService.getIncidentEntity(incident.getId()))
                .thenReturn(incident);

        when(actionRepository.findByCode("INVALIDATE_REDIS_CACHE"))
                .thenReturn(Optional.of(autoApprovedAction));

        when(executionRepository.save(any()))
                .thenAnswer(inv -> {
                    RemediationExecution execution = inv.getArgument(0);

                    if (execution.getId() == null) {
                        execution.setId(UUID.randomUUID());
                    }

                    return execution;
                });

        when(topics.remediationRequested())
                .thenReturn("remediation.requested");

        var response = remediationService.requestRemediation(
                incident.getId(),
                new RequestRemediationRequest("INVALIDATE_REDIS_CACHE"),
                engineer
        );

        assertThat(response.status())
                .isEqualTo("APPROVED");

        verify(eventPublisher)
                .publish(
                        eq("remediation.requested"),
                        anyString(),
                        any()
                );
    }

    @Test
    void requestRemediation_rejectsDisabledAction() {

        when(incidentService.getIncidentEntity(incident.getId()))
                .thenReturn(incident);

        RemediationAction disabled = RemediationAction.builder()
                .code("DISABLED_ACTION")
                .name("x")
                .description("x")
                .riskLevel(RiskLevel.LOW)
                .requiredRole(Role.ENGINEER)
                .approvalRequired(false)
                .enabled(false)
                .build();

        when(actionRepository.findByCode("DISABLED_ACTION"))
                .thenReturn(Optional.of(disabled));

        assertThatThrownBy(() ->
                remediationService.requestRemediation(
                        incident.getId(),
                        new RequestRemediationRequest("DISABLED_ACTION"),
                        engineer
                ))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void approveRemediation_transitionsToApprovedAndDispatchesExecution() {

        RemediationExecution execution =
                pendingExecution(approvalRequiredAction);

        when(executionRepository.findById(execution.getId()))
                .thenReturn(Optional.of(execution));

        when(executionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        when(topics.remediationRequested())
                .thenReturn("remediation.requested");

        var response =
                remediationService.approveRemediation(
                        execution.getId(),
                        engineer
                );

        assertThat(response.status())
                .isEqualTo("APPROVED");

        verify(eventPublisher)
                .publish(
                        eq("remediation.requested"),
                        anyString(),
                        any()
                );
    }

    @Test
    void rejectRemediation_setsRejectedAndDoesNotDispatch() {

        RemediationExecution execution =
                pendingExecution(approvalRequiredAction);

        when(executionRepository.findById(execution.getId()))
                .thenReturn(Optional.of(execution));

        when(executionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        var response =
                remediationService.rejectRemediation(
                        execution.getId(),
                        new RejectRemediationRequest(
                                "Too risky right now"
                        ),
                        engineer
                );

        assertThat(response.status())
                .isEqualTo("REJECTED");

        verify(eventPublisher, never())
                .publish(any(), any(), any());
    }

    @Test
    void runExecution_successPath_resolvesIncidentAndNotifiesReporter() {

        RemediationExecution execution =
                approvedExecution(approvalRequiredAction);

        when(executionRepository.findById(execution.getId()))
                .thenReturn(Optional.of(execution));

        when(executionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        when(executorFactory.getActiveExecutor())
                .thenReturn(executor);

        when(executor.execute(
                eq(approvalRequiredAction),
                any()
        )).thenReturn(
                RemediationExecutionOutcome.success("done")
        );

        when(healthChecker.check(
                anyString(),
                anyString()
        )).thenReturn(true);

        when(topics.remediationCompleted())
                .thenReturn("remediation.completed");

        remediationService.runExecution(
                execution.getId(),
                "corr-1"
        );

        assertThat(execution.getStatus())
                .isEqualTo(RemediationExecutionStatus.SUCCEEDED);

        assertThat(execution.getHealthCheckPassed())
                .isTrue();

        verify(incidentService)
                .transitionForRemediation(
                        eq(incident.getId()),
                        eq(IncidentStatus.RESOLVED),
                        anyString(),
                        eq(engineer),
                        anyString()
                );

        /*
         * Reporter and assigned engineer are different users, so the
         * reporter receives exactly one completion notification.
         */
        verify(notificationService)
                .notify(
                        eq(reporter),
                        eq(com.servicedesk.notification.NotificationType.REMEDIATION_COMPLETED),
                        anyString(),
                        eq(incident.getId())
                );
    }

    @Test
    void runExecution_failureWithinRetryLimit_schedulesAnotherAttempt() {

        RemediationExecution execution =
                approvedExecution(approvalRequiredAction);

        execution.setAttemptNumber(1);

        when(executionRepository.findById(execution.getId()))
                .thenReturn(Optional.of(execution));

        when(executionRepository.save(any()))
                .thenAnswer(inv -> {
                    RemediationExecution saved = inv.getArgument(0);

                    if (saved.getId() == null) {
                        saved.setId(UUID.randomUUID());
                    }

                    return saved;
                });

        when(executorFactory.getActiveExecutor())
                .thenReturn(executor);

        when(executor.execute(
                eq(approvalRequiredAction),
                any()
        )).thenReturn(
                RemediationExecutionOutcome.failure(
                        "connection refused"
                )
        );

        when(topics.remediationFailed())
                .thenReturn("remediation.failed");

        when(topics.remediationRequested())
                .thenReturn("remediation.requested");

        Incident afterIncrement =
                sameIncidentWithAttempts(1);

        when(incidentService.getIncidentEntity(
                incident.getId()
        )).thenReturn(afterIncrement);

        remediationService.runExecution(
                execution.getId(),
                "corr-2"
        );

        assertThat(execution.getStatus())
                .isEqualTo(RemediationExecutionStatus.FAILED);

        verify(incidentService)
                .incrementRemediationAttempts(
                        incident.getId()
                );

        verify(incidentService)
                .transitionForRemediation(
                        eq(incident.getId()),
                        eq(IncidentStatus.REMEDIATION_PENDING),
                        anyString(),
                        eq(engineer)
                );

        ArgumentCaptor<RemediationExecution> retryCaptor =
                ArgumentCaptor.forClass(
                        RemediationExecution.class
                );

        /*
         * Three saves happen:
         *
         * 1. RUNNING
         * 2. FAILED
         * 3. New APPROVED retry execution
         */
        verify(executionRepository, times(3))
                .save(retryCaptor.capture());

        assertThat(retryCaptor.getValue().getAttemptNumber())
                .isEqualTo(2);

        assertThat(retryCaptor.getValue().getStatus())
                .isEqualTo(RemediationExecutionStatus.APPROVED);

        verify(eventPublisher)
                .publish(
                        eq("remediation.requested"),
                        anyString(),
                        any()
                );
    }

    @Test
    void runExecution_failureExceedingRetryLimit_escalatesInstead() {

        RemediationAction singleAttemptAction =
                RemediationAction.builder()
                        .code("RESTART_CONNECTION_POOL")
                        .name("Restart Connection Pool")
                        .description("desc")
                        .riskLevel(RiskLevel.MEDIUM)
                        .requiredRole(Role.ENGINEER)
                        .approvalRequired(true)
                        .timeoutSeconds(10)
                        .retryLimit(1)
                        .enabled(true)
                        .build();

        singleAttemptAction.setId(UUID.randomUUID());

        RemediationExecution execution =
                approvedExecution(singleAttemptAction);

        execution.setAttemptNumber(1);

        when(executionRepository.findById(execution.getId()))
                .thenReturn(Optional.of(execution));

        when(executionRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        when(executorFactory.getActiveExecutor())
                .thenReturn(executor);

        when(executor.execute(
                eq(singleAttemptAction),
                any()
        )).thenReturn(
                RemediationExecutionOutcome.failure(
                        "still broken"
                )
        );

        when(topics.remediationFailed())
                .thenReturn("remediation.failed");

        Incident afterIncrement =
                sameIncidentWithAttempts(1);

        when(incidentService.getIncidentEntity(
                incident.getId()
        )).thenReturn(afterIncrement);

        remediationService.runExecution(
                execution.getId(),
                "corr-3"
        );

        assertThat(execution.getStatus())
                .isEqualTo(RemediationExecutionStatus.FAILED);

        verify(incidentService)
                .transitionForRemediation(
                        eq(incident.getId()),
                        eq(IncidentStatus.ESCALATED),
                        anyString(),
                        eq(engineer)
                );

        verify(eventPublisher, never())
                .publish(
                        eq("remediation.requested"),
                        any(),
                        any()
                );
    }

    @Test
    void runExecution_skipsIfExecutionIsNotApproved() {

        RemediationExecution execution =
                pendingExecution(approvalRequiredAction);

        when(executionRepository.findById(execution.getId()))
                .thenReturn(Optional.of(execution));

        remediationService.runExecution(
                execution.getId(),
                "corr-4"
        );

        verifyNoInteractions(
                executorFactory,
                healthChecker
        );

        verify(
                incidentService,
                never()
        ).transitionForRemediation(
                any(),
                any(),
                any(),
                any()
        );
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private RemediationExecution pendingExecution(
            RemediationAction action
    ) {

        RemediationExecution execution =
                RemediationExecution.builder()
                        .incident(incident)
                        .remediationAction(action)
                        .status(
                                RemediationExecutionStatus.PENDING_APPROVAL
                        )
                        .requestedBy(engineer)
                        .attemptNumber(1)
                        .idempotencyKey(
                                UUID.randomUUID().toString()
                        )
                        .build();

        execution.setId(UUID.randomUUID());

        return execution;
    }

    private RemediationExecution approvedExecution(
            RemediationAction action
    ) {

        RemediationExecution execution =
                pendingExecution(action);

        execution.setStatus(
                RemediationExecutionStatus.APPROVED
        );

        execution.setApprovedBy(engineer);

        return execution;
    }

    private Incident sameIncidentWithAttempts(
            int attempts
    ) {

        Incident copy = Incident.builder()
                .title(incident.getTitle())
                .description(incident.getDescription())
                .category(incident.getCategory())
                .severity(incident.getSeverity())
                .status(incident.getStatus())
                .reporter(incident.getReporter())
                .assignedEngineer(
                        incident.getAssignedEngineer()
                )
                .remediationAttempts(attempts)
                .build();

        copy.setId(incident.getId());

        return copy;
    }
}