package com.ticketflow.order.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ticks {@link OutboxRelay}.
 *
 * <p>Deliberately a separate bean. Putting {@code @Scheduled} next to the
 * {@code @Transactional} work would mean the timer calls the method on {@code this},
 * skipping the proxy - and the relay would fail every cycle with "no transaction is
 * known to be in progress". Going through another bean keeps the proxy in the path,
 * and means the integration test calling {@code dispatchBatch()} exercises exactly
 * what the scheduler does.
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelay relay;

    public OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${ticketflow.outbox.poll-interval:1000}")
    public void dispatchScheduled() {
        try {
            relay.dispatchBatch();
        } catch (RuntimeException e) {
            // The scheduler abandons a task that throws. Swallow, log, retry next tick.
            log.error("Outbox dispatch cycle failed", e);
        }
    }
}
