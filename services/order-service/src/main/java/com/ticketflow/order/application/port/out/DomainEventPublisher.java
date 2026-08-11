package com.ticketflow.order.application.port.out;

import com.ticketflow.order.domain.event.OrderCreated;

/**
 * Driven port: how the application announces that something happened.
 *
 * <p>"Publish" here means <em>record in the outbox</em>, inside the same transaction
 * as the business write - not "send to Kafka now". A use case calling a Kafka
 * producer directly could commit an order whose event was never published, which is
 * exactly the failure the outbox exists to prevent.
 */
public interface DomainEventPublisher {

    void publish(OrderCreated event);
}
