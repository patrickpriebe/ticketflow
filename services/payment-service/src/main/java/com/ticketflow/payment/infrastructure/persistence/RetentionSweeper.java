package com.ticketflow.payment.infrastructure.persistence;

import com.ticketflow.payment.infrastructure.persistence.jpa.JpaOutboxRepository;
import com.ticketflow.payment.infrastructure.persistence.jpa.JpaProcessedEventRepository;
import com.ticketflow.payment.infrastructure.persistence.jpa.JpaWebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Apaga o que as tabelas de apoio já não precisam guardar.
 *
 * <p>Mesma ideia do Order Service, com uma tabela a mais: aqui existe também o
 * inbox do webhook do Stripe. As três só cresciam, e nenhuma quebra nada num dia
 * — a conta chega meses depois, como consulta lenta e disco cheio, sem nada
 * apontando para a causa.
 *
 * <p><strong>Nada que envolva dinheiro é apagado.</strong> {@code payments} e
 * {@code payment_attempts} ficam: são o registro do que foi cobrado de quem, e a
 * pergunta "esta pessoa pagou?" não pode depender de uma janela de retenção. O
 * que sai daqui é só material de mecanismo — evento já publicado, marca de
 * mensagem já consumida, marca de webhook já aplicado.
 *
 * <p>As duas janelas de inbox precisam ser maiores que o prazo de reentrega de
 * quem as alimenta: o Kafka guarda 7 dias, e o Stripe reenvia por dias enquanto
 * não receber 200. Encurtá-las reabre exatamente o defeito que as tabelas fecham,
 * e o sintoma seria cobrança duplicada muito depois, sem ligação aparente.
 */
@Component
public class RetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(RetentionSweeper.class);

    private final JpaOutboxRepository outbox;
    private final JpaProcessedEventRepository processedEvents;
    private final JpaWebhookEventRepository webhookEvents;
    private final Clock clock;
    private final Duration outboxRetention;
    private final Duration inboxRetention;
    private final Duration webhookRetention;
    private final int batchSize;

    public RetentionSweeper(JpaOutboxRepository outbox,
                            JpaProcessedEventRepository processedEvents,
                            JpaWebhookEventRepository webhookEvents,
                            Clock clock,
                            @Value("${ticketflow.retention.outbox:P7D}") Duration outboxRetention,
                            @Value("${ticketflow.retention.inbox:P30D}") Duration inboxRetention,
                            @Value("${ticketflow.retention.webhook:P30D}") Duration webhookRetention,
                            @Value("${ticketflow.retention.batch-size:500}") int batchSize) {
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.webhookEvents = webhookEvents;
        this.clock = clock;
        this.outboxRetention = outboxRetention;
        this.inboxRetention = inboxRetention;
        this.webhookRetention = webhookRetention;
        this.batchSize = batchSize;
    }

    /** Uma passada. Devolve quantas linhas saíram, somando as três tabelas. */
    @Transactional
    public int sweep() {
        Instant now = clock.instant();

        int publishedRemoved = outbox.deletePublishedBefore(now.minus(outboxRetention), batchSize);
        int inboxRemoved = processedEvents.deleteProcessedBefore(now.minus(inboxRetention), batchSize);
        int webhooksRemoved = webhookEvents.deleteReceivedBefore(now.minus(webhookRetention), batchSize);

        int total = publishedRemoved + inboxRemoved + webhooksRemoved;
        if (total > 0) {
            log.info("Retenção: {} publicada(s), {} de inbox e {} de webhook removidos",
                    publishedRemoved, inboxRemoved, webhooksRemoved);
        }
        return total;
    }
}
