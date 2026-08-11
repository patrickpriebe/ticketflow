package com.ticketflow.order.infrastructure.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.order.infrastructure.persistence.entity.OutboxMessageEntity;
import com.ticketflow.order.infrastructure.persistence.jpa.JpaOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ships rows from {@code outbox_messages} to Kafka.
 *
 * <p>This is the second half of the transactional outbox. The first half - writing
 * the event in the same transaction as the order - already happened; here the event
 * finally leaves the database.
 *
 * <p><strong>Delivery is at-least-once, on purpose.</strong> The message is sent
 * first and only then marked PUBLISHED, so a crash in between resends it. The
 * alternative - marking first - would lose events, which nothing downstream can
 * recover from. Duplicates, by contrast, are handled: every consumer deduplicates on
 * {@code eventId} through its inbox table.
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
     * Publishes one batch, transactionally.
     *
     * <p>The timer lives in {@link OutboxRelayScheduler}, a separate bean, and not in
     * this class. That is not cosmetic: a {@code @Scheduled} method calling
     * {@code this.dispatchBatch()} would bypass the transaction proxy entirely and
     * the pessimistic lock would fail with "no transaction is known to be in
     * progress". Crossing a bean boundary is what makes {@code @Transactional} apply.
     *
     * @return how many messages were claimed in this cycle
     */
    @Transactional
    public int dispatchBatch() {
        List<OutboxMessageEntity> batch =
                outbox.findDispatchable(clock.instant(), PageRequest.of(0, batchSize));

        for (OutboxMessageEntity message : batch) {
            dispatch(message);
        }

        if (!batch.isEmpty()) {
            log.debug("Outbox relay handled {} message(s)", batch.size());
        }
        return batch.size();
    }

    private void dispatch(OutboxMessageEntity message) {
        try {
            publisher.publish(
                    message.getTopic(),
                    message.getPartitionKey(),
                    message.getPayload(),
                    headersOf(message));
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

    /** Exponential backoff, capped - a broker that is down should not be hammered. */
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
}
