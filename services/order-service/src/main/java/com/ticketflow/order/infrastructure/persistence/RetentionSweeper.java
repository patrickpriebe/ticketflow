package com.ticketflow.order.infrastructure.persistence;

import com.ticketflow.order.infrastructure.persistence.jpa.JpaOutboxRepository;
import com.ticketflow.order.infrastructure.persistence.jpa.JpaProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Apaga o que as duas tabelas de apoio já não precisam guardar.
 *
 * <p>Nem o outbox nem o inbox tinham limpeza nenhuma. Cada pedido deixa uma linha
 * em {@code outbox_messages} que fica lá para sempre depois de publicada, e cada
 * mensagem consumida deixa uma em {@code processed_events} — as duas só crescem.
 * Nada quebra por isso num dia, e é justamente esse o problema: a conta chega
 * como consulta que foi ficando lenta e disco que acabou, meses depois, sem nada
 * apontando para a causa. A coleção equivalente no Mongo já nasceu com TTL de 30
 * dias; estas duas ficaram sem o equivalente.
 *
 * <p><strong>O que cada janela protege é diferente</strong>, e por isso são duas.
 * No outbox só sai o que já foi publicado — {@code PENDING} ainda tem que sair e
 * {@code FAILED} é o que alguém precisa investigar, então apagar qualquer um dos
 * dois seria perder evento. No inbox o risco é o oposto: o registro é o que
 * impede uma reentrega de ser processada de novo, então a janela tem que cobrir
 * a retenção do tópico com folga. Os tópicos guardam 7 dias, o padrão daqui é 30.
 *
 * <p>Sempre em lote limitado. Uma varredura que apaga meses de uma vez segura
 * lock e transação por tempo imprevisível, e o pool nas instâncias publicadas tem
 * cinco conexões.
 */
@Component
public class RetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(RetentionSweeper.class);

    private final JpaOutboxRepository outbox;
    private final JpaProcessedEventRepository processedEvents;
    private final Clock clock;
    private final Duration outboxRetention;
    private final Duration inboxRetention;
    private final int batchSize;

    public RetentionSweeper(JpaOutboxRepository outbox,
                            JpaProcessedEventRepository processedEvents,
                            Clock clock,
                            @Value("${ticketflow.retention.outbox:P7D}") Duration outboxRetention,
                            @Value("${ticketflow.retention.inbox:P30D}") Duration inboxRetention,
                            @Value("${ticketflow.retention.batch-size:500}") int batchSize) {
        this.outbox = outbox;
        this.processedEvents = processedEvents;
        this.clock = clock;
        this.outboxRetention = outboxRetention;
        this.inboxRetention = inboxRetention;
        this.batchSize = batchSize;
    }

    /**
     * Uma passada. Devolve quantas linhas saíram, somando as duas tabelas.
     *
     * <p>Transacional e chamada de outro bean, pela mesma razão do relay: um
     * {@code @Scheduled} aqui dentro chamaria o método em {@code this} e passaria
     * por fora do proxy.
     */
    @Transactional
    public int sweep() {
        Instant now = clock.instant();

        int publishedRemoved = outbox.deletePublishedBefore(now.minus(outboxRetention), batchSize);
        int inboxRemoved = processedEvents.deleteProcessedBefore(now.minus(inboxRetention), batchSize);

        if (publishedRemoved > 0 || inboxRemoved > 0) {
            log.info("Retenção: {} mensagem(ns) publicada(s) e {} registro(s) de inbox removidos",
                    publishedRemoved, inboxRemoved);
        }
        return publishedRemoved + inboxRemoved;
    }
}
