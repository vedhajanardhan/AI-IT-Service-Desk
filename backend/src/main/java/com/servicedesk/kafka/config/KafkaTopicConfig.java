package com.servicedesk.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares every Kafka topic the platform needs. Spring Kafka's
 * KafkaAdmin will create these automatically against the configured
 * broker on startup (idempotent - safe to run repeatedly).
 */
@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaTopicConfig {

    private final KafkaTopicsProperties topics;

    public KafkaTopicConfig(KafkaTopicsProperties topics) {
        this.topics = topics;
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean public NewTopic incidentCreatedTopic() { return topic(topics.incidentCreated()); }
    @Bean public NewTopic incidentAssignedTopic() { return topic(topics.incidentAssigned()); }
    @Bean public NewTopic incidentAnalyzedTopic() { return topic(topics.incidentAnalyzed()); }
    @Bean public NewTopic remediationRequestedTopic() { return topic(topics.remediationRequested()); }
    @Bean public NewTopic remediationStartedTopic() { return topic(topics.remediationStarted()); }
    @Bean public NewTopic remediationCompletedTopic() { return topic(topics.remediationCompleted()); }
    @Bean public NewTopic remediationFailedTopic() { return topic(topics.remediationFailed()); }
    @Bean public NewTopic incidentResolvedTopic() { return topic(topics.incidentResolved()); }
    @Bean public NewTopic incidentEscalatedTopic() { return topic(topics.incidentEscalated()); }
    @Bean public NewTopic deadLetterTopic() { return topic(topics.deadLetter()); }
}
