package com.ticketflow.order.domain.exception;

import java.util.UUID;

/**
 * Another transaction changed the same order first (optimistic lock on
 * {@code orders.version}).
 *
 * <p>For a Kafka consumer this is a retryable failure, not a bug: letting the
 * message be redelivered is exactly the right response.
 */
public class ConcurrentOrderUpdateException extends DomainException {

    public ConcurrentOrderUpdateException(UUID orderId) {
        super("concurrent-order-update",
                "Order %s was modified by another transaction. Retry.".formatted(orderId));
    }
}
