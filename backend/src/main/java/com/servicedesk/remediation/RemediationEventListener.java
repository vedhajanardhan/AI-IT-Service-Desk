package com.servicedesk.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.common.CorrelationIdFilter;
import com.servicedesk.kafka.IdempotencyGuard;
import com.servicedesk.kafka.event.RemediationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Reacts to RemediationRequested events by actually running the
 * remediation - this is the "meaningful asynchronous workflow" Kafka is
 * used for here: approving remediation over REST returns immediately,
 * and the potentially slow/retried execution happens on this consumer
 * thread instead of blocking the engineer's HTTP request.
 */
@Component
@RequiredArgsConstructor
public class RemediationEventListener {

    private static final Logger log = LoggerFactory.getLogger(RemediationEventListener.class);

    private final RemediationService remediationService;
    private final IdempotencyGuard idempotencyGuard;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @KafkaListener(topics = "${app.kafka.topics.remediation-requested}")
    public void onRemediationRequested(String payload, Acknowledgment ack) {
        try {
            RemediationRequestedEvent event = objectMapper.readValue(payload, RemediationRequestedEvent.class);
            String correlationId = "remediation-" + event.remediationExecutionId();
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);

            // Idempotency: a redelivered message for the same execution ID
            // must not run the action twice. Each retry attempt gets its
            // own execution row/ID upstream, so this is safe to key on
            // execution ID alone.
            String claimKey = "remediation-execution:" + event.remediationExecutionId();
            if (!idempotencyGuard.tryClaim(claimKey)) {
                log.info("Skipping already-processed remediation execution {}", event.remediationExecutionId());
                ack.acknowledge();
                return;
            }

            log.info("Processing remediation execution {} for incident {}",
                    event.remediationExecutionId(), event.incidentId());
            remediationService.runExecution(event.remediationExecutionId(), correlationId);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process remediation.requested message: {}", e.getMessage(), e);
            // Do not ack - the common error handler (retry + dead-letter) takes over.
            throw new RuntimeException("Remediation event processing failed", e);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
