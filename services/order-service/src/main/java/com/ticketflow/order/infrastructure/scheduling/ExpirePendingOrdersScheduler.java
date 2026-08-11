package com.ticketflow.order.infrastructure.scheduling;

import com.ticketflow.order.application.port.in.ExpirePendingOrdersUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the expiry sweep periodically.
 *
 * <p>A separate bean from the work it triggers, like the outbox relay: the use case
 * opens its own transaction per order through {@code UnitOfWork}, and keeping the
 * timer outside means nothing here depends on proxy behaviour.
 *
 * <p>Switchable so an integration test can drive the sweep itself instead of racing
 * a background thread.
 */
@Component
@ConditionalOnProperty(name = "ticketflow.order.expiry.enabled",
        havingValue = "true", matchIfMissing = true)
public class ExpirePendingOrdersScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirePendingOrdersScheduler.class);

    private final ExpirePendingOrdersUseCase expirePendingOrders;

    public ExpirePendingOrdersScheduler(ExpirePendingOrdersUseCase expirePendingOrders) {
        this.expirePendingOrders = expirePendingOrders;
    }

    @Scheduled(fixedDelayString = "${ticketflow.order.expiry.interval:60000}")
    public void sweep() {
        try {
            expirePendingOrders.execute();
        } catch (RuntimeException e) {
            // The scheduler abandons a task that throws; this one must keep running
            // or reserved inventory would never be released again.
            log.error("Order expiry sweep failed", e);
        }
    }
}
