package com.servicedesk.incident;

import com.servicedesk.common.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentStateMachineTest {

    private final IncidentStateMachine stateMachine = new IncidentStateMachine();

    @Test
    void allowsHappyPathTransitions() {
        assertThat(stateMachine.canTransition(IncidentStatus.OPEN, IncidentStatus.TRIAGED)).isTrue();
        assertThat(stateMachine.canTransition(IncidentStatus.TRIAGED, IncidentStatus.AI_ANALYZED)).isTrue();
        assertThat(stateMachine.canTransition(IncidentStatus.AI_ANALYZED, IncidentStatus.ASSIGNED)).isTrue();
        assertThat(stateMachine.canTransition(IncidentStatus.ASSIGNED, IncidentStatus.REMEDIATION_PENDING)).isTrue();
        assertThat(stateMachine.canTransition(IncidentStatus.REMEDIATION_PENDING, IncidentStatus.REMEDIATION_RUNNING)).isTrue();
        assertThat(stateMachine.canTransition(IncidentStatus.REMEDIATION_RUNNING, IncidentStatus.VALIDATING)).isTrue();
        assertThat(stateMachine.canTransition(IncidentStatus.VALIDATING, IncidentStatus.RESOLVED)).isTrue();
    }

    @Test
    void allowsEscalationFromAlmostAnyInFlightState() {
        assertThat(stateMachine.canTransition(IncidentStatus.OPEN, IncidentStatus.ESCALATED)).isTrue();
        assertThat(stateMachine.canTransition(IncidentStatus.REMEDIATION_RUNNING, IncidentStatus.ESCALATED)).isTrue();
        assertThat(stateMachine.canTransition(IncidentStatus.VALIDATING, IncidentStatus.ESCALATED)).isTrue();
    }

    @Test
    void allowsRetryLoopBackToRemediationPending() {
        assertThat(stateMachine.canTransition(IncidentStatus.REMEDIATION_RUNNING, IncidentStatus.REMEDIATION_PENDING)).isTrue();
        assertThat(stateMachine.canTransition(IncidentStatus.VALIDATING, IncidentStatus.REMEDIATION_PENDING)).isTrue();
    }

    @Test
    void allowsReopeningAResolvedIncident() {
        assertThat(stateMachine.canTransition(IncidentStatus.RESOLVED, IncidentStatus.REOPENED)).isTrue();
    }

    @Test
    void rejectsSkippingStatesForward() {
        assertThat(stateMachine.canTransition(IncidentStatus.OPEN, IncidentStatus.RESOLVED)).isFalse();
    }

    @Test
    void rejectsMovingBackwardsOutsideDefinedRetryPaths() {
        assertThat(stateMachine.canTransition(IncidentStatus.RESOLVED, IncidentStatus.OPEN)).isFalse();
        assertThat(stateMachine.canTransition(IncidentStatus.ASSIGNED, IncidentStatus.OPEN)).isFalse();
    }

    @Test
    void rejectsTransitioningToSameState() {
        assertThat(stateMachine.canTransition(IncidentStatus.OPEN, IncidentStatus.OPEN)).isFalse();
    }

    @Test
    void resolvedIncidentCannotBeResolvedAgain() {
        assertThat(stateMachine.canTransition(IncidentStatus.RESOLVED, IncidentStatus.RESOLVED)).isFalse();
    }

    @Test
    void validateTransitionThrowsOnIllegalMove() {
        assertThatThrownBy(() -> stateMachine.validateTransition(IncidentStatus.OPEN, IncidentStatus.RESOLVED))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("OPEN")
                .hasMessageContaining("RESOLVED");
    }

    @Test
    void validateTransitionIsSilentOnLegalMove() {
        stateMachine.validateTransition(IncidentStatus.OPEN, IncidentStatus.TRIAGED);
        // no exception = pass
    }

    @Test
    void escalatedIncidentCanReturnToTriageOrBeResolved() {
        assertThat(stateMachine.canTransition(IncidentStatus.ESCALATED, IncidentStatus.TRIAGED)).isTrue();
        assertThat(stateMachine.canTransition(IncidentStatus.ESCALATED, IncidentStatus.RESOLVED)).isTrue();
    }
}
