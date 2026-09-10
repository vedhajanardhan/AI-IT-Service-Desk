package com.servicedesk.incident;

import com.servicedesk.common.BaseEntity;
import com.servicedesk.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per meaningful thing that happened to an incident: status
 * changes, AI analysis completion, remediation attempts, assignments,
 * escalations. This is what powers the Incident Details timeline and
 * doubles as an audit trail.
 */
@Entity
@Table(name = "incident_events", indexes = {
        @Index(name = "idx_incident_events_incident", columnList = "incident_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentEventType eventType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    private String previousStatus;
    private String newStatus;
}
