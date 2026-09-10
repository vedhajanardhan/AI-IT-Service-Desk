package com.servicedesk.remediation;

import com.servicedesk.common.BaseEntity;
import com.servicedesk.incident.Incident;
import com.servicedesk.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "remediation_executions", indexes = {
        @Index(name = "idx_remediation_executions_incident", columnList = "incident_id"),
        @Index(name = "idx_remediation_executions_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemediationExecution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "remediation_action_id", nullable = false)
    private RemediationAction remediationAction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RemediationExecutionStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Builder.Default
    private int attemptNumber = 1;

    private Instant startedAt;
    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    private Boolean healthCheckPassed;

    /**
     * Stable key derived from (incident, action, attemptNumber) so a
     * redelivered Kafka message can never execute this attempt twice -
     * see IdempotencyGuard.
     */
    @Column(nullable = false, unique = true)
    private String idempotencyKey;
}
