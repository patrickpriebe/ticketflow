package com.ticketflow.order.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aciona o {@link RetentionSweeper}.
 *
 * <p>Bean separado pelo mesmo motivo do {@code OutboxRelayScheduler}: o timer
 * chamando o método na própria instância passaria por fora do proxy e a
 * transação nunca existiria.
 *
 * <p>Roda de hora em hora por padrão, e não a cada minuto. Não há pressa — o que
 * se está evitando é crescimento de meses — e cada passada é uma escrita no
 * banco que compete com o tráfego real.
 *
 * <p>Uma passada limitada por lote pode não dar conta do acumulado de uma vez.
 * Está certo assim: a tabela encolhe algumas centenas de linhas por hora até
 * alcançar a janela, em vez de um {@code delete} gigante que segura lock.
 */
@Component
@ConditionalOnProperty(name = "ticketflow.retention.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final RetentionSweeper sweeper;
    private final int maxBatchesPerCycle;

    public RetentionScheduler(RetentionSweeper sweeper,
                              @Value("${ticketflow.retention.max-batches-per-cycle:10}") int maxBatchesPerCycle) {
        this.sweeper = sweeper;
        this.maxBatchesPerCycle = maxBatchesPerCycle;
    }

    @Scheduled(fixedDelayString = "${ticketflow.retention.interval:3600000}",
               initialDelayString = "${ticketflow.retention.initial-delay:60000}")
    public void sweepScheduled() {
        try {
            // Vários lotes por ciclo, com teto. Sem o teto, a primeira passada numa
            // tabela que acumulou meses viraria exatamente a varredura longa que os
            // lotes existem para evitar.
            for (int cycle = 0; cycle < maxBatchesPerCycle; cycle++) {
                if (sweeper.sweep() == 0) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            // O agendador desiste de uma tarefa que lança. Engolir, registrar e
            // tentar no próximo ciclo — mesma escolha do relay.
            log.error("Ciclo de retenção falhou", e);
        }
    }
}
