package com.ticketflow.payment.infrastructure.observability;

import com.ticketflow.payment.infrastructure.persistence.jpa.JpaOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * O mesmo instrumento que o Order Service já tinha, e que aqui faltava.
 *
 * <p>A falta era pior do que parece. Este serviço tem o próprio outbox, e é por
 * ele que sai o {@code PAGAMENTO_APROVADO}. Um relay parado aqui significa
 * dinheiro cobrado no provedor e nenhum pedido saindo de PENDING — o cliente
 * pagou, o extrato dele mostra a cobrança, e o site continua dizendo que está
 * aguardando. Não havia número nenhum que denunciasse isso.
 *
 * <p>Duas séries porque falham de formas diferentes: PENDING subindo é relay
 * atrasado; FAILED acima de zero é mensagem que desistiu e precisa de gente.
 */
@Component
public class OutboxMetrics {

    public OutboxMetrics(JpaOutboxRepository outbox, MeterRegistry registry) {
        Gauge.builder("ticketflow.outbox.messages", () -> outbox.countByStatus("PENDING"))
                .description("Payment results written but not yet published to Kafka")
                .tag("status", "pending")
                .register(registry);

        Gauge.builder("ticketflow.outbox.messages", () -> outbox.countByStatus("FAILED"))
                .description("Payment results that exhausted their publish attempts")
                .tag("status", "failed")
                .register(registry);

        Gauge.builder("ticketflow.outbox.oldest.pending.seconds", () -> ageInSeconds(outbox))
                .description("Age of the oldest payment result still waiting to be published")
                .baseUnit("seconds")
                .register(registry);
    }

    private static double ageInSeconds(JpaOutboxRepository outbox) {
        Instant oldest = outbox.oldestPendingCreatedAt();
        if (oldest == null) return 0;
        return Math.max(0, Duration.between(oldest, Instant.now()).toMillis() / 1000d);
    }
}
