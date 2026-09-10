package com.servicedesk.kafka;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around KafkaTemplate. Publish failures are logged but never
 * propagate - a broker hiccup must not break incident creation, assignment,
 * or resolution, all of which are meant to work as normal synchronous REST
 * operations first and foremost. Kafka carries side-effects (notifications,
 * analytics, remediation orchestration), not the primary write.
 */
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event to topic {} (key={}): {}", topic, key, ex.getMessage());
                }
            });
        } catch (Exception ex) {
            log.error("Unexpected error publishing to topic {} (key={})", topic, key, ex);
        }
    }
}
