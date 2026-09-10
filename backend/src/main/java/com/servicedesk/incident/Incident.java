package com.servicedesk.incident;

import com.servicedesk.common.BaseEntity;
import com.servicedesk.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incidents_status", columnList = "status"),
        @Index(name = "idx_incidents_reporter", columnList = "reporter_id"),
        @Index(name = "idx_incidents_assigned_engineer", columnList = "assigned_engineer_id"),
        @Index(name = "idx_incidents_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    private IncidentPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IncidentStatus status = IncidentStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_engineer_id")
    private User assignedEngineer;

    @Column(columnDefinition = "TEXT")
    private String resolutionDetails;

    private Instant resolvedAt;
    private Instant escalatedAt;
    private String escalationReason;

    /**
     * Denormalized counter of remediation retry attempts for this incident,
     * used by the remediation engine's retry-then-escalate policy.
     */
    @Builder.Default
    private int remediationAttempts = 0;
}
