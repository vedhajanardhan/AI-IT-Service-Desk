package com.servicedesk.remediation;

import com.servicedesk.common.exception.BadRequestException;
import com.servicedesk.common.exception.UnauthorizedActionException;
import com.servicedesk.incident.Incident;
import com.servicedesk.incident.IncidentStatus;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Backend policy validation - the "Policy Validation" step in the
 * Incident -> AI Diagnosis -> ... -> Policy Validation -> Approval ->
 * Execution workflow. This runs independently of whatever the AI
 * recommended or whatever the frontend sent, so a compromised client
 * can't skip it.
 */
@Component
public class RemediationPolicyValidator {

    private static final Set<IncidentStatus> ELIGIBLE_STATUSES = Set.of(
            IncidentStatus.AI_ANALYZED,
            IncidentStatus.ASSIGNED,
            IncidentStatus.REMEDIATION_PENDING
    );

    public void validateCanRequest(
            RemediationAction action,
            Incident incident,
            User requestedBy
    ) {
        if (!action.isEnabled()) {
            throw new BadRequestException(
                    "Remediation action '" + action.getCode()
                            + "' is currently disabled"
            );
        }

        if (!ELIGIBLE_STATUSES.contains(incident.getStatus())) {
            throw new BadRequestException(
                    "Incident must be AI_ANALYZED, ASSIGNED, or REMEDIATION_PENDING " +
                            "to request remediation (currently "
                            + incident.getStatus() + ")"
            );
        }

        requireRoleAtLeast(action.getRequiredRole(), requestedBy);
    }

    public void validateCanApprove(
            RemediationAction action,
            User approver
    ) {
        /*
         * Approval must respect the remediation action's required role.
         *
         * For example:
         * ENGINEER action -> ENGINEER or ADMIN can approve
         * ADMIN action    -> only ADMIN can approve
         */
        requireRoleAtLeast(action.getRequiredRole(), approver);
    }

    /**
     * ENGINEER and ADMIN can act on ENGINEER-level actions.
     * Only ADMIN can act on ADMIN-level actions.
     * EMPLOYEE can never act on remediation actions.
     */
    private void requireRoleAtLeast(
            Role required,
            User actor
    ) {
        if (actor.getRole() == Role.EMPLOYEE) {
            throw new UnauthorizedActionException(
                    "Role " + actor.getRole()
                            + " is not permitted to act on remediation actions"
            );
        }

        if (required == Role.ADMIN
                && actor.getRole() != Role.ADMIN) {
            throw new UnauthorizedActionException(
                    "This action requires the ADMIN role (actor has "
                            + actor.getRole() + ")"
            );
        }
    }
}
