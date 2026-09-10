package com.servicedesk.incident;

import com.servicedesk.common.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for which incident status transitions are legal.
 *
 * Happy path:
 *   OPEN -> TRIAGED -> AI_ANALYZED -> ASSIGNED -> REMEDIATION_PENDING
 *         -> REMEDIATION_RUNNING -> VALIDATING -> RESOLVED
 *
 * Failure path from almost any in-flight state:
 *   * -> ESCALATED
 *
 * Resolved incidents can be reopened, which routes back into triage.
 *
 * This is intentionally a plain, testable class (not tied to Spring State
 * Machine) so the rules are easy to read, unit test, and reason about.
 */
@Component
public class IncidentStateMachine {

    private static final Map<IncidentStatus, Set<IncidentStatus>> TRANSITIONS = new EnumMap<>(IncidentStatus.class);

    static {
        TRANSITIONS.put(IncidentStatus.OPEN, EnumSet.of(
                IncidentStatus.TRIAGED, IncidentStatus.ESCALATED));

        TRANSITIONS.put(IncidentStatus.TRIAGED, EnumSet.of(
                IncidentStatus.AI_ANALYZED, IncidentStatus.ASSIGNED, IncidentStatus.ESCALATED));

        TRANSITIONS.put(IncidentStatus.AI_ANALYZED, EnumSet.of(
                IncidentStatus.ASSIGNED, IncidentStatus.REMEDIATION_PENDING, IncidentStatus.ESCALATED));

        TRANSITIONS.put(IncidentStatus.ASSIGNED, EnumSet.of(
                IncidentStatus.REMEDIATION_PENDING, IncidentStatus.RESOLVED, IncidentStatus.ESCALATED));

        TRANSITIONS.put(IncidentStatus.REMEDIATION_PENDING, EnumSet.of(
                IncidentStatus.REMEDIATION_RUNNING, IncidentStatus.ESCALATED));

        TRANSITIONS.put(IncidentStatus.REMEDIATION_RUNNING, EnumSet.of(
                IncidentStatus.VALIDATING, IncidentStatus.REMEDIATION_PENDING /* retry */, IncidentStatus.ESCALATED));

        TRANSITIONS.put(IncidentStatus.VALIDATING, EnumSet.of(
                IncidentStatus.RESOLVED, IncidentStatus.REMEDIATION_PENDING /* retry */, IncidentStatus.ESCALATED));

        TRANSITIONS.put(IncidentStatus.RESOLVED, EnumSet.of(
                IncidentStatus.REOPENED));

        TRANSITIONS.put(IncidentStatus.REOPENED, EnumSet.of(
                IncidentStatus.TRIAGED, IncidentStatus.ASSIGNED, IncidentStatus.ESCALATED));

        TRANSITIONS.put(IncidentStatus.ESCALATED, EnumSet.of(
                IncidentStatus.ASSIGNED, IncidentStatus.TRIAGED, IncidentStatus.RESOLVED));
    }

    public boolean canTransition(IncidentStatus from, IncidentStatus to) {
        if (from == to) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Throws if the transition is illegal; returns silently otherwise.
     */
    public void validateTransition(IncidentStatus from, IncidentStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(
                    "Cannot transition incident from " + from + " to " + to);
        }
    }

    public Set<IncidentStatus> allowedNextStates(IncidentStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }
}
