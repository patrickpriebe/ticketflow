package com.ticketflow.order.domain.event;

import com.ticketflow.order.domain.model.Order;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The fact that an order was placed.
 *
 * <p>A domain event, not a Kafka message: it knows nothing about topics, envelopes
 * or serialisation. Turning this into the wire format described by
 * {@code contracts/events/order-created.v1.schema.json} is the job of the outbox
 * adapter in the infrastructure layer.
 *
 * @param eventId    the message identity consumers deduplicate on
 * @param occurredAt when the order was placed, not when it will be published
 */
public record OrderCreated(UUID eventId, Instant occurredAt, Order order, String ticketEventName) {

    public static final String TYPE = "ORDER_CREATED";

    public OrderCreated {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(order, "order is required");
        Objects.requireNonNull(ticketEventName, "ticketEventName is required");
    }

    public static OrderCreated of(Order order, String ticketEventName, Instant occurredAt) {
        return new OrderCreated(UUID.randomUUID(), occurredAt, order, ticketEventName);
    }
}
