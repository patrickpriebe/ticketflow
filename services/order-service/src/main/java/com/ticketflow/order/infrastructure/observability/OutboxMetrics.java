package com.ticketflow.order.infrastructure.observability;

import com.ticketflow.order.infrastructure.persistence.jpa.JpaOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

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

        // A idade da mais velha, não só quantas há. Cinquenta mensagens de dois
        // segundos é pico de tráfego; uma parada há dez minutos é incidente — e
        // as duas coisas têm a mesma cara num gráfico de quantidade.
        //
        // Zero quando a fila está vazia, e não "sem dado": um painel que some
        // quando está tudo bem ensina a ignorar o painel.
        Gauge.builder("ticketflow.outbox.oldest.pending.seconds", () -> ageInSeconds(outbox))
                .description("Age of the oldest event still waiting to be published")
                .baseUnit("seconds")
                .register(registry);
    }

    private static double ageInSeconds(JpaOutboxRepository outbox) {
        Instant oldest = outbox.oldestPendingCreatedAt();
        if (oldest == null) return 0;
        return Math.max(0, Duration.between(oldest, Instant.now()).toMillis() / 1000d);
    }
}
