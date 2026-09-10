package com.servicedesk.ai;

import com.servicedesk.common.BaseEntity;
import com.servicedesk.incident.Incident;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per AI analysis attempt against an incident (there can be more
 * than one - e.g. a re-analysis after new comments come in). The latest
 * SUCCEEDED row for an incident is what the remediation flow and the
 * Incident Details screen show as "the" analysis.
 */
@Entity
@Table(name = "ai_analyses", indexes = {
        @Index(name = "idx_ai_analyses_incident", columnList = "incident_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysis extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiAnalysisStatus status;

    private String provider;

    private String classification;
    private String severityRecommendation;
    private String priorityRecommendation;

    @Column(columnDefinition = "TEXT")
    private String rootCause;

    private Double confidenceScore;
    private String recommendedActionCode;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    /** Comma-separated - kept simple rather than a join table for a v1 tagging feature. */
    private String relevantKnowledgeTags;

    @Column(columnDefinition = "TEXT")
    private String failureReason;
}
