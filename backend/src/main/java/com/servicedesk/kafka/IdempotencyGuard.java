package com.servicedesk.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Prevents a redelivered/duplicate Kafka message from being processed
 * twice - e.g. a RemediationRequested event retried by the broker must not
 * trigger the same remediation action a second time.
 *
 * Uses SETNX semantics (setIfAbsent) so the check-and-mark is atomic.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /**
     * Returns true the first time this key is seen (and marks it as seen),
     * false on every subsequent call within the TTL window.
     */
    public boolean tryClaim(String idempotencyKey) {
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + idempotencyKey, "processed", TTL);
        return Boolean.TRUE.equals(firstTime);
    }
}
