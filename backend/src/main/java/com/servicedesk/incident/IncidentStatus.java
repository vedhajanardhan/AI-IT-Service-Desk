package com.servicedesk.incident;

public enum IncidentStatus {
    OPEN,
    TRIAGED,
    AI_ANALYZED,
    ASSIGNED,
    REMEDIATION_PENDING,
    REMEDIATION_RUNNING,
    VALIDATING,
    RESOLVED,
    ESCALATED,
    REOPENED
}
