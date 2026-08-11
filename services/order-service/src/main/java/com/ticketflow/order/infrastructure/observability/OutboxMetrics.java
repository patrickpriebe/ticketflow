package com.ticketflow.order.infrastructure.observability;

import com.ticketflow.order.infrastructure.persistence.jpa.JpaOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Publishes the outbox backlog as a gauge.
 *
 * <p>This is the single most useful number in the whole system. An outbox that keeps
 * growing means the relay stopped, or the broker is unreachable, and orders are
 * being accepted while nobody downstream ever hears about them - a failure that is
 * completely invisible from the API, which happily keeps answering 202.
 *
 * <p>Two series, because they fail differently: PENDING climbing means the relay is
 * behind, FAILED above zero means messages gave up and a human has to look.
 */
@Component
public class OutboxMetrics {

    public OutboxMetrics(JpaOutboxRepository outbox, MeterRegistry registry) {
        Gauge.builder("ticketflow.outbox.messages", () -> outbox.countByStatus("PENDING"))
                .description("Events written but not yet published to Kafka")
                .tag("status", "pending")
                .register(registry);

        Gauge.builder("ticketflow.outbox.messages", () -> outbox.countByStatus("FAILED"))
                .description("Events that exhausted their publish attempts")
                .tag("status", "failed")
                .register(registry);
    }
}
