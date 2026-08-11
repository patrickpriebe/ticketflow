package com.ticketflow.order.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One entry of the order's audit trail. Append-only: the front-end renders this
 * list as the "acompanhe seu pedido" timeline.
 *
 * @param sourceEventId the Kafka message that caused the transition, when there was
 *                      one. Null for the initial PENDING entry, which is created by
 *                      the HTTP request itself.
 */
public record OrderStatusChange(OrderStatus fromStatus,
                                OrderStatus toStatus,
                                String reason,
                                UUID sourceEventId,
                                Instant occurredAt) {

    public OrderStatusChange {
        Objects.requireNonNull(toStatus, "toStatus is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    static OrderStatusChange initial(Instant occurredAt) {
        return new OrderStatusChange(null, OrderStatus.PENDING, "Order placed", null, occurredAt);
    }
}
