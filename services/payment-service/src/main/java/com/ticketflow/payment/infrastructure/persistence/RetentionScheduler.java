package com.ticketflow.payment.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aciona o {@link RetentionSweeper}.
 *
 * <p>Bean separado pelo mesmo motivo do relay: {@code @Scheduled} junto do
 * {@code @Transactional} faria o timer chamar o método na própria instância,
 * passando por fora do proxy — e a transação nunca existiria.
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
            for (int cycle = 0; cycle < maxBatchesPerCycle; cycle++) {
                if (sweeper.sweep() == 0) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.error("Ciclo de retenção falhou", e);
        }
    }
}
