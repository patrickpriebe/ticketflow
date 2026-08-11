package com.ticketflow.payment.infrastructure.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.payment.infrastructure.persistence.entity.OutboxMessageEntity;
import com.ticketflow.payment.infrastructure.persistence.jpa.JpaOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ships settled payments from the outbox to Kafka.
 *
 * <p>Sends first, marks PUBLISHED second, so delivery is at-least-once. Duplicates
 * are handled downstream by each consumer's inbox; a lost event would not be.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private final JpaOutboxRepository outbox;
    private final MessagePublisher publisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxRelay(JpaOutboxRepository outbox,
                       MessagePublisher publisher,
                       ObjectMapper objectMapper,
                       Clock clock,
                       @Value("${ticketflow.outbox.batch-size:50}") int batchSize,
                       @Value("${ticketflow.outbox.max-attempts:8}") int maxAttempts) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /**
     * The timer lives in {@link Scheduler}, a separate bean: a {@code @Scheduled}
     * method calling this one on {@code this} would bypass the transaction proxy and
     * the pessimistic lock would fail with "no transaction is known to be in
     * progress". The Order Service learned that the hard way.
     */
    @Transactional
    public int dispatchBatch() {
        List<OutboxMessageEntity> batch =
                outbox.findDispatchable(clock.instant(), PageRequest.of(0, batchSize));

        for (OutboxMessageEntity message : batch) {
            dispatch(message);
        }
        return batch.size();
    }

    private void dispatch(OutboxMessageEntity message) {
        try {
            publisher.publish(message.getTopic(), message.getPartitionKey(),
                    message.getPayload(), headersOf(message));
            message.markPublished(clock.instant());

        } catch (RuntimeException e) {
            int attemptsSoFar = message.getAttempts() + 1;
            if (attemptsSoFar >= maxAttempts) {
                log.error("Giving up on outbox message {} after {} attempts",
                        message.getId(), attemptsSoFar, e);
                message.giveUp(e.toString());
            } else {
                Instant retryAt = clock.instant().plus(backoffFor(attemptsSoFar));
                log.warn("Outbox message {} failed (attempt {}), retrying at {}",
                        message.getId(), attemptsSoFar, retryAt);
                message.markFailed(e.toString(), retryAt);
            }
        }
    }

    private static Duration backoffFor(int attempt) {
        Duration backoff = Duration.ofSeconds(1L << Math.min(attempt, 20));
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    private Map<String, Object> headersOf(OutboxMessageEntity message) {
        try {
            return objectMapper.readValue(message.getHeaders(), new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("Ignoring unreadable headers on outbox message {}", message.getId());
            return Map.of();
        }
    }

    /** Separate bean so {@code @Transactional} on {@link #dispatchBatch()} actually applies. */
    @Component
    public static class Scheduler {

        private final OutboxRelay relay;

        public Scheduler(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelayString = "${ticketflow.outbox.poll-interval:1000}")
        public void dispatchScheduled() {
            try {
                relay.dispatchBatch();
            } catch (RuntimeException e) {
                // The scheduler abandons a task that throws. Log and retry next tick.
                log.error("Outbox dispatch cycle failed", e);
            }
        }
    }
}
