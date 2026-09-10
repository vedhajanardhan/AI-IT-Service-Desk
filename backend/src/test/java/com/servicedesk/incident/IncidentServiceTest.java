package com.servicedesk.incident;

import com.servicedesk.common.exception.BadRequestException;
import com.servicedesk.common.exception.InvalidStateTransitionException;
import com.servicedesk.common.exception.ResourceNotFoundException;
import com.servicedesk.common.exception.UnauthorizedActionException;
import com.servicedesk.incident.dto.*;
import com.servicedesk.kafka.EventPublisher;
import com.servicedesk.kafka.config.KafkaTopicsProperties;
import com.servicedesk.notification.NotificationService;
import com.servicedesk.user.Role;
import com.servicedesk.user.User;
import com.servicedesk.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IncidentService using real IncidentStateMachine/IncidentMapper
 * (cheap, pure logic - no reason to mock them) and Mockito mocks for
 * everything with I/O (repositories, Kafka, notifications). Covers the
 * spec's required flows: create incident, assign incident, incident
 * resolution, escalation, and unauthorized access.
 */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentEventRepository eventRepository;

    @Mock
    private IncidentCommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private KafkaTopicsProperties topics;

    @Mock
    private NotificationService notificationService;

    private IncidentService incidentService;

    private User reporter;
    private User engineer;
    private User otherEmployee;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(
                incidentRepository,
                eventRepository,
                commentRepository,
                userRepository,
                new IncidentStateMachine(),
                new IncidentMapper(),
                eventPublisher,
                topics,
                notificationService
        );

        reporter = withId(
                User.builder()
                        .fullName("Riya Reporter")
                        .role(Role.EMPLOYEE)
                        .build()
        );

        engineer = withId(
                User.builder()
                        .fullName("Evan Engineer")
                        .role(Role.ENGINEER)
                        .build()
        );

        otherEmployee = withId(
                User.builder()
                        .fullName("Olu Other")
                        .role(Role.EMPLOYEE)
                        .build()
        );
    }

    @Test
    void createIncident_savesAsOpenAndPublishesCreatedEvent() {

        /*
         * The real database would generate the Incident ID.
         * In this unit test, we simulate that behavior in the repository mock.
         */
        when(incidentRepository.save(any(Incident.class)))
                .thenAnswer(inv -> {
                    Incident saved = inv.getArgument(0);

                    if (saved.getId() == null) {
                        saved.setId(UUID.randomUUID());
                    }

                    return saved;
                });

        when(topics.incidentCreated())
                .thenReturn("incident.created");

        CreateIncidentRequest request = new CreateIncidentRequest(
                "DB timeouts",
                "Queries timing out",
                IncidentCategory.DATABASE,
                IncidentSeverity.HIGH
        );

        IncidentResponse response =
                incidentService.createIncident(request, reporter);

        assertThat(response.status())
                .isEqualTo(IncidentStatus.OPEN);

        assertThat(response.reporterId())
                .isEqualTo(reporter.getId());

        verify(eventPublisher)
                .publish(
                        eq("incident.created"),
                        anyString(),
                        any()
                );

        verify(eventRepository)
                .save(argThat(e ->
                        e.getEventType() == IncidentEventType.CREATED
                ));
    }

    @Test
    void assignIncident_movesToAssignedAndNotifiesEngineer() {

        /*
         * The state machine does not allow:
         * OPEN -> ASSIGNED
         *
         * Assignment is allowed after triage.
         */
        Incident incident = openIncident();
        incident.setStatus(IncidentStatus.TRIAGED);

        when(incidentRepository.findById(incident.getId()))
                .thenReturn(Optional.of(incident));

        when(userRepository.findById(engineer.getId()))
                .thenReturn(Optional.of(engineer));

        IncidentResponse response =
                incidentService.assignIncident(
                        incident.getId(),
                        new AssignIncidentRequest(engineer.getId()),
                        engineer
                );

        assertThat(response.status())
                .isEqualTo(IncidentStatus.ASSIGNED);

        assertThat(response.assignedEngineerId())
                .isEqualTo(engineer.getId());

        verify(notificationService)
                .notify(
                        eq(engineer),
                        any(),
                        anyString(),
                        eq(incident.getId())
                );
    }

    @Test
    void assignIncident_rejectsAssigningToAnEmployee() {

        Incident incident = openIncident();

        when(incidentRepository.findById(incident.getId()))
                .thenReturn(Optional.of(incident));

        when(userRepository.findById(otherEmployee.getId()))
                .thenReturn(Optional.of(otherEmployee));

        assertThatThrownBy(() ->
                incidentService.assignIncident(
                        incident.getId(),
                        new AssignIncidentRequest(otherEmployee.getId()),
                        engineer
                ))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resolveIncident_setsResolutionDetailsAndTimestampAndPublishesEvent() {

        Incident incident = assignedIncident();

        when(incidentRepository.findById(incident.getId()))
                .thenReturn(Optional.of(incident));

        when(topics.incidentResolved())
                .thenReturn("incident.resolved");

        IncidentResponse response =
                incidentService.resolveIncident(
                        incident.getId(),
                        new ResolveIncidentRequest("Restarted the service"),
                        engineer
                );

        assertThat(response.status())
                .isEqualTo(IncidentStatus.RESOLVED);

        assertThat(response.resolutionDetails())
                .isEqualTo("Restarted the service");

        assertThat(response.resolvedAt())
                .isNotNull();

        verify(notificationService)
                .notify(
                        eq(reporter),
                        any(),
                        anyString(),
                        eq(incident.getId())
                );
    }

    @Test
    void resolveIncident_rejectsResolvingAFreshlyOpenedIncident() {

        Incident incident = openIncident();

        when(incidentRepository.findById(incident.getId()))
                .thenReturn(Optional.of(incident));

        assertThatThrownBy(() ->
                incidentService.resolveIncident(
                        incident.getId(),
                        new ResolveIncidentRequest("too fast"),
                        engineer
                ))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void escalateIncident_notifiesBothReporterAndAssignedEngineer() {

        Incident incident = assignedIncident();

        when(incidentRepository.findById(incident.getId()))
                .thenReturn(Optional.of(incident));

        when(topics.incidentEscalated())
                .thenReturn("incident.escalated");

        IncidentResponse response =
                incidentService.escalateIncident(
                        incident.getId(),
                        new EscalateIncidentRequest(
                                "Repeated remediation failures"
                        ),
                        engineer
                );

        assertThat(response.status())
                .isEqualTo(IncidentStatus.ESCALATED);

        assertThat(response.escalationReason())
                .isEqualTo("Repeated remediation failures");

        verify(notificationService)
                .notify(
                        eq(engineer),
                        any(),
                        anyString(),
                        eq(incident.getId())
                );

        verify(notificationService)
                .notify(
                        eq(reporter),
                        any(),
                        anyString(),
                        eq(incident.getId())
                );
    }

    @Test
    void getIncident_unauthorizedAccess_employeeCannotViewSomeoneElsesIncident() {

        Incident incident = openIncident();

        when(incidentRepository.findById(incident.getId()))
                .thenReturn(Optional.of(incident));

        assertThatThrownBy(() ->
                incidentService.getIncident(
                        incident.getId(),
                        otherEmployee
                ))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    void getIncident_employeeCanViewTheirOwnIncident() {

        Incident incident = openIncident();

        when(incidentRepository.findById(incident.getId()))
                .thenReturn(Optional.of(incident));

        IncidentResponse response =
                incidentService.getIncident(
                        incident.getId(),
                        reporter
                );

        assertThat(response.id())
                .isEqualTo(incident.getId());
    }

    @Test
    void getIncident_throwsNotFound_forUnknownId() {

        UUID unknownId = UUID.randomUUID();

        when(incidentRepository.findById(unknownId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                incidentService.getIncident(
                        unknownId,
                        engineer
                ))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Incident openIncident() {

        Incident incident = Incident.builder()
                .title("Sample incident")
                .description("Something is wrong")
                .category(IncidentCategory.APPLICATION_ERROR)
                .severity(IncidentSeverity.MEDIUM)
                .status(IncidentStatus.OPEN)
                .reporter(reporter)
                .remediationAttempts(0)
                .build();

        return withId(incident);
    }

    private Incident assignedIncident() {

        Incident incident = openIncident();

        incident.setStatus(IncidentStatus.ASSIGNED);
        incident.setAssignedEngineer(engineer);

        return incident;
    }

    /**
     * BaseEntity's id lives on the superclass, so plain @Builder doesn't
     * expose it - set it directly.
     */
    private <T extends com.servicedesk.common.BaseEntity> T withId(T entity) {

        entity.setId(UUID.randomUUID());

        return entity;
    }
}