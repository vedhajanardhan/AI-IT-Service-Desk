package com.servicedesk.remediation;

import com.servicedesk.common.exception.BadRequestException;
import com.servicedesk.common.exception.ResourceNotFoundException;
import com.servicedesk.incident.Incident;
import com.servicedesk.incident.IncidentEventType;
import com.servicedesk.incident.IncidentService;
import com.servicedesk.incident.IncidentStatus;
import com.servicedesk.kafka.EventPublisher;
import com.servicedesk.kafka.IdempotencyGuard;
import com.servicedesk.kafka.config.KafkaTopicsProperties;
import com.servicedesk.kafka.event.RemediationCompletedEvent;
import com.servicedesk.kafka.event.RemediationFailedEvent;
import com.servicedesk.kafka.event.RemediationRequestedEvent;
import com.servicedesk.kafka.event.RemediationStartedEvent;
import com.servicedesk.notification.NotificationService;
import com.servicedesk.notification.NotificationType;
import com.servicedesk.remediation.dto.*;
import com.servicedesk.remediation.executor.RemediationExecutionOutcome;
import com.servicedesk.remediation.executor.RemediationExecutor;
import com.servicedesk.remediation.executor.RemediationExecutorFactory;
import com.servicedesk.remediation.health.HealthChecker;
import com.servicedesk.user.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class RemediationService {

    private static final Logger log = LoggerFactory.getLogger(RemediationService.class);

    private final RemediationActionRepository actionRepository;
    private final RemediationExecutionRepository executionRepository;
    private final RemediationPolicyValidator policyValidator;
    private final RemediationExecutorFactory executorFactory;
    private final HealthChecker healthChecker;
    private final IncidentService incidentService;
    private final EventPublisher eventPublisher;
    private final KafkaTopicsProperties topics;
    private final NotificationService notificationService;

    // --- Catalog ---

    @Transactional(readOnly = true)
    public List<RemediationActionResponse> listActions() {
        return actionRepository.findAll().stream().map(this::toActionResponse).toList();
    }

    // --- Request / approve / reject (human-facing, synchronous) ---

    @Transactional
    public RemediationExecutionResponse requestRemediation(UUID incidentId, RequestRemediationRequest request, User requester) {
        Incident incident = incidentService.getIncidentEntity(incidentId);
        RemediationAction action = actionRepository.findByCode(request.actionCode())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown remediation action: " + request.actionCode()));

        policyValidator.validateCanRequest(action, incident, requester);

        int attemptNumber = incident.getRemediationAttempts() + 1;
        RemediationExecutionStatus initialStatus = action.isApprovalRequired()
                ? RemediationExecutionStatus.PENDING_APPROVAL
                : RemediationExecutionStatus.APPROVED;

        RemediationExecution execution = RemediationExecution.builder()
                .incident(incident)
                .remediationAction(action)
                .status(initialStatus)
                .requestedBy(requester)
                .attemptNumber(attemptNumber)
                .idempotencyKey(idempotencyKey(incidentId, action.getCode(), attemptNumber))
                .build();
        execution = executionRepository.save(execution);

        if (incident.getStatus() != IncidentStatus.REMEDIATION_PENDING) {
            incidentService.transitionForRemediation(incidentId, IncidentStatus.REMEDIATION_PENDING,
                    "Remediation requested: " + action.getName(), requester);
        }
        incidentService.recordIncidentEvent(incidentId, IncidentEventType.REMEDIATION_RECOMMENDED,
                action.getName() + " requested by " + requester.getFullName()
                        + (action.isApprovalRequired() ? " (awaiting approval)" : " (auto-approved, low risk)"),
                requester);

        if (!action.isApprovalRequired()) {
            dispatchForExecution(execution);
        } else if (incident.getAssignedEngineer() != null) {
            notificationService.notify(incident.getAssignedEngineer(), NotificationType.REMEDIATION_APPROVAL_REQUIRED,
                    "\"" + action.getName() + "\" needs your approval for incident \"" + incident.getTitle() + "\".",
                    incidentId);
        }

        return toExecutionResponse(execution);
    }

    @Transactional
    public RemediationExecutionResponse approveRemediation(UUID executionId, User approver) {
        RemediationExecution execution = findExecutionOrThrow(executionId);
        if (execution.getStatus() != RemediationExecutionStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only PENDING_APPROVAL executions can be approved (currently "
                    + execution.getStatus() + ")");
        }
        policyValidator.validateCanApprove(execution.getRemediationAction(), approver);

        execution.setStatus(RemediationExecutionStatus.APPROVED);
        execution.setApprovedBy(approver);
        executionRepository.save(execution);

        incidentService.recordIncidentEvent(execution.getIncident().getId(), IncidentEventType.REMEDIATION_APPROVED,
                execution.getRemediationAction().getName() + " approved by " + approver.getFullName(), approver);

        dispatchForExecution(execution);
        return toExecutionResponse(execution);
    }

    @Transactional
    public RemediationExecutionResponse rejectRemediation(UUID executionId, RejectRemediationRequest request, User approver) {
        RemediationExecution execution = findExecutionOrThrow(executionId);
        if (execution.getStatus() != RemediationExecutionStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Only PENDING_APPROVAL executions can be rejected (currently "
                    + execution.getStatus() + ")");
        }
        policyValidator.validateCanApprove(execution.getRemediationAction(), approver);

        execution.setStatus(RemediationExecutionStatus.REJECTED);
        execution.setApprovedBy(approver);
        execution.setFailureReason(request.reason());
        executionRepository.save(execution);

        incidentService.recordIncidentEvent(execution.getIncident().getId(), IncidentEventType.REMEDIATION_REJECTED,
                execution.getRemediationAction().getName() + " rejected by " + approver.getFullName()
                        + ": " + request.reason(), approver);

        return toExecutionResponse(execution);
    }

    @Transactional(readOnly = true)
    public List<RemediationExecutionResponse> getExecutionHistory(UUID incidentId) {
        return executionRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(this::toExecutionResponse)
                .toList();
    }

    /** Publishes the RemediationRequested event that the Kafka consumer picks up to actually run it. */
    private void dispatchForExecution(RemediationExecution execution) {
        eventPublisher.publish(topics.remediationRequested(), execution.getId().toString(),
                new RemediationRequestedEvent(execution.getIncident().getId(), execution.getId(),
                        execution.getRemediationAction().getCode(), Instant.now()));
    }

    private String idempotencyKey(UUID incidentId, String actionCode, int attemptNumber) {
        return incidentId + ":" + actionCode + ":" + attemptNumber;
    }

    private RemediationExecution findExecutionOrThrow(UUID id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Remediation execution not found: " + id));
    }

    // --- Async execution, called from the Kafka consumer thread ---

    /**
     * Runs the actual remediation + health check for an APPROVED execution.
     * Called asynchronously off the Kafka listener, never from the HTTP
     * approval request thread - approving remediation returns immediately;
     * the work happens on the consumer thread. Idempotency is enforced by
     * the caller (RemediationEventListener) before this is invoked.
     */
    @Transactional
    public void runExecution(UUID executionId, String correlationId) {
        RemediationExecution execution = findExecutionOrThrow(executionId);
        if (execution.getStatus() != RemediationExecutionStatus.APPROVED) {
            log.warn("Skipping execution {} - status is {} not APPROVED (already processed or invalid state)",
                    executionId, execution.getStatus());
            return;
        }

        UUID incidentId = execution.getIncident().getId();
        RemediationAction action = execution.getRemediationAction();
        User systemActor = execution.getApprovedBy() != null ? execution.getApprovedBy() : execution.getRequestedBy();

        incidentService.transitionForRemediation(incidentId, IncidentStatus.REMEDIATION_RUNNING,
                "Executing " + action.getName() + " (attempt " + execution.getAttemptNumber() + ")", systemActor);

        execution.setStatus(RemediationExecutionStatus.RUNNING);
        execution.setStartedAt(Instant.now());
        executionRepository.save(execution);

        incidentService.recordIncidentEvent(incidentId, IncidentEventType.REMEDIATION_STARTED,
                action.getName() + " started (attempt " + execution.getAttemptNumber() + ")", systemActor);
        eventPublisher.publish(topics.remediationStarted(), incidentId.toString(),
                new RemediationStartedEvent(incidentId, executionId, execution.getAttemptNumber(), Instant.now()));

        RemediationExecutionOutcome outcome = runWithTimeout(action, incidentId.toString(), correlationId);

        if (!outcome.success()) {
            handleFailure(execution, action, incidentId, systemActor, outcome.failureReason());
            return;
        }

        incidentService.transitionForRemediation(incidentId, IncidentStatus.VALIDATING,
                "Validating remediation result for " + action.getName(), systemActor);

        boolean healthy = healthChecker.check(incidentId.toString(), action.getCode());
        execution.setHealthCheckPassed(healthy);

        if (healthy) {
            execution.setStatus(RemediationExecutionStatus.SUCCEEDED);
            execution.setCompletedAt(Instant.now());
            executionRepository.save(execution);

            incidentService.recordIncidentEvent(incidentId, IncidentEventType.HEALTH_CHECK_PASSED,
                    "Health check passed after " + action.getName(), systemActor);
            incidentService.recordIncidentEvent(incidentId, IncidentEventType.REMEDIATION_SUCCEEDED,
                    action.getName() + " succeeded", systemActor);

            incidentService.transitionForRemediation(incidentId, IncidentStatus.RESOLVED,
                    "Auto-resolved: " + outcome.message(), systemActor,
                    "Automatically resolved by remediation action '" + action.getName() + "'. " + outcome.message());

            eventPublisher.publish(topics.remediationCompleted(), incidentId.toString(),
                    new RemediationCompletedEvent(incidentId, executionId, true, Instant.now()));

            notificationService.notify(execution.getIncident().getReporter(), NotificationType.REMEDIATION_COMPLETED,
                    "\"" + action.getName() + "\" resolved your incident \"" + execution.getIncident().getTitle()
                            + "\" automatically.", incidentId);
            if (execution.getIncident().getAssignedEngineer() != null) {
                notificationService.notify(execution.getIncident().getAssignedEngineer(), NotificationType.REMEDIATION_COMPLETED,
                        "\"" + action.getName() + "\" completed successfully for \"" + execution.getIncident().getTitle() + "\".",
                        incidentId);
            }
        } else {
            incidentService.recordIncidentEvent(incidentId, IncidentEventType.HEALTH_CHECK_FAILED,
                    "Health check failed after " + action.getName(), systemActor);
            handleFailure(execution, action, incidentId, systemActor,
                    "Remediation action reported success but the post-remediation health check failed");
        }
    }

    private void handleFailure(RemediationExecution execution, RemediationAction action, UUID incidentId,
                                User actor, String reason) {
        execution.setStatus(RemediationExecutionStatus.FAILED);
        execution.setFailureReason(reason);
        execution.setCompletedAt(Instant.now());
        executionRepository.save(execution);

        incidentService.incrementRemediationAttempts(incidentId);
        incidentService.recordIncidentEvent(incidentId, IncidentEventType.REMEDIATION_FAILED,
                action.getName() + " failed (attempt " + execution.getAttemptNumber() + "): " + reason, actor);

        Incident refreshed = incidentService.getIncidentEntity(incidentId);
        boolean willRetry = refreshed.getRemediationAttempts() < action.getRetryLimit();

        eventPublisher.publish(topics.remediationFailed(), incidentId.toString(),
                new RemediationFailedEvent(incidentId, execution.getId(), reason,
                        execution.getAttemptNumber(), willRetry, Instant.now()));

        if (execution.getIncident().getAssignedEngineer() != null) {
            notificationService.notify(execution.getIncident().getAssignedEngineer(), NotificationType.REMEDIATION_FAILED,
                    "\"" + action.getName() + "\" failed for \"" + execution.getIncident().getTitle() + "\""
                            + (willRetry ? " - retrying automatically." : " - escalated after exhausting retries."),
                    incidentId);
        }

        if (willRetry) {
            incidentService.transitionForRemediation(incidentId, IncidentStatus.REMEDIATION_PENDING,
                    "Retrying " + action.getName() + " after failure", actor);

            RemediationExecution retry = RemediationExecution.builder()
                    .incident(execution.getIncident())
                    .remediationAction(action)
                    .status(RemediationExecutionStatus.APPROVED) // already approved once; auto-retry doesn't re-prompt
                    .requestedBy(execution.getRequestedBy())
                    .approvedBy(execution.getApprovedBy())
                    .attemptNumber(execution.getAttemptNumber() + 1)
                    .idempotencyKey(idempotencyKey(incidentId, action.getCode(), execution.getAttemptNumber() + 1))
                    .build();
            executionRepository.save(retry);
            dispatchForExecution(retry);
        } else {
            incidentService.transitionForRemediation(incidentId, IncidentStatus.ESCALATED,
                    "Auto-remediation exhausted its retry limit (" + action.getRetryLimit()
                            + ") for " + action.getName() + ": " + reason, actor);
        }
    }

    /**
     * Wraps the (synchronous, mock or real) executor call with a hard
     * timeout so a hung remediation action can't block the Kafka consumer
     * thread indefinitely - satisfies the "Timeout handling" requirement
     * independently of whatever the executor implementation does itself.
     */
    private RemediationExecutionOutcome runWithTimeout(RemediationAction action, String incidentId, String correlationId) {
        RemediationExecutor executor = executorFactory.getActiveExecutor();
        RemediationExecutor.ExecutionContext context = new RemediationExecutor.ExecutionContext(incidentId, correlationId);

        // Uses the common ForkJoinPool via the no-executor overload rather than
        // a per-call thread/executor - this method runs on a Kafka consumer
        // thread, not a request thread, so borrowing from the common pool for
        // a short-lived, bounded (timeoutSeconds-capped) task is appropriate
        // and keeps this compiling on plain Java 17 (virtual threads need 21+).
        CompletableFuture<RemediationExecutionOutcome> future = CompletableFuture.supplyAsync(
                () -> executor.execute(action, context));

        try {
            return future.get(action.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return RemediationExecutionOutcome.failure(
                    "Remediation action timed out after " + action.getTimeoutSeconds() + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RemediationExecutionOutcome.failure("Remediation execution was interrupted");
        } catch (ExecutionException e) {
            return RemediationExecutionOutcome.failure(
                    "Remediation executor threw an exception: " + e.getCause().getMessage());
        }
    }

    // --- Mapping ---

    private RemediationActionResponse toActionResponse(RemediationAction a) {
        return new RemediationActionResponse(a.getId(), a.getCode(), a.getName(), a.getDescription(),
                a.getRiskLevel().name(), a.getRequiredRole().name(), a.isApprovalRequired(),
                a.getTimeoutSeconds(), a.getRetryLimit(), a.isEnabled());
    }

    private RemediationExecutionResponse toExecutionResponse(RemediationExecution e) {
        return new RemediationExecutionResponse(e.getId(), e.getIncident().getId(),
                e.getRemediationAction().getCode(), e.getRemediationAction().getName(), e.getStatus().name(),
                e.getRequestedBy().getFullName(), e.getApprovedBy() != null ? e.getApprovedBy().getFullName() : null,
                e.getAttemptNumber(), e.getStartedAt(), e.getCompletedAt(), e.getFailureReason(),
                e.getHealthCheckPassed(), e.getCreatedAt());
    }
}
