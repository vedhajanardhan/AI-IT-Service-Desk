package com.servicedesk.remediation;

import com.servicedesk.common.exception.BadRequestException;
import com.servicedesk.common.exception.UnauthorizedActionException;
import com.servicedesk.incident.Incident;
import com.servicedesk.incident.IncidentStatus;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemediationPolicyValidatorTest {

    private final RemediationPolicyValidator validator = new RemediationPolicyValidator();

    private RemediationAction enabledAction;
    private RemediationAction disabledAction;
    private Incident eligibleIncident;
    private Incident ineligibleIncident;
    private User employee;
    private User engineer;
    private User admin;

    @BeforeEach
    void setUp() {
        enabledAction = RemediationAction.builder()
                .code("RESTART_APPLICATION_SERVICE").requiredRole(Role.ENGINEER).enabled(true).build();
        disabledAction = RemediationAction.builder()
                .code("RESTART_CONNECTION_POOL").requiredRole(Role.ENGINEER).enabled(false).build();

        eligibleIncident = Incident.builder().status(IncidentStatus.AI_ANALYZED).build();
        ineligibleIncident = Incident.builder().status(IncidentStatus.OPEN).build();

        employee = User.builder().role(Role.EMPLOYEE).build();
        engineer = User.builder().role(Role.ENGINEER).build();
        admin = User.builder().role(Role.ADMIN).build();
    }

    @Test
    void allowsEngineerToRequestEnabledActionOnEligibleIncident() {
        assertThatCode(() -> validator.validateCanRequest(enabledAction, eligibleIncident, engineer))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDisabledAction() {
        assertThatThrownBy(() -> validator.validateCanRequest(disabledAction, eligibleIncident, engineer))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void rejectsIncidentInWrongStatus() {
        assertThatThrownBy(() -> validator.validateCanRequest(enabledAction, ineligibleIncident, engineer))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("AI_ANALYZED");
    }

    @Test
    void rejectsEmployeeRequestingRemediation() {
        assertThatThrownBy(() -> validator.validateCanRequest(enabledAction, eligibleIncident, employee))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    void allowsAdminEverywhereEngineerIsAllowed() {
        assertThatCode(() -> validator.validateCanRequest(enabledAction, eligibleIncident, admin))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmployeeApprovingRemediation() {
        assertThatThrownBy(() -> validator.validateCanApprove(enabledAction, employee))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    void allowsEngineerApprovingEngineerLevelAction() {
        assertThatCode(() -> validator.validateCanApprove(enabledAction, engineer))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEngineerApprovingAdminOnlyAction() {
        RemediationAction adminOnlyAction = RemediationAction.builder()
                .code("DANGEROUS_ACTION").requiredRole(Role.ADMIN).enabled(true).build();

        assertThatThrownBy(() -> validator.validateCanApprove(adminOnlyAction, engineer))
                .isInstanceOf(UnauthorizedActionException.class);
    }
}
