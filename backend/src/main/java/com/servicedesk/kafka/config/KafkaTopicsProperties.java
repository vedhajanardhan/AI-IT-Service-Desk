package com.servicedesk.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicsProperties(
        String incidentCreated,
        String incidentAssigned,
        String incidentAnalyzed,
        String remediationRequested,
        String remediationStarted,
        String remediationCompleted,
        String remediationFailed,
        String incidentResolved,
        String incidentEscalated,
        String deadLetter
) {}
