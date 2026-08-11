package com.ticketflow.order.infrastructure.messaging;

import java.util.Map;

/**
 * The narrow seam between the outbox relay and the broker.
 *
 * <p>Exists so {@link OutboxRelay} - which holds the retry and give-up logic worth
 * testing - can be unit-tested without Spring Cloud Stream in the picture.
 */
public interface MessagePublisher {

    /**
     * @param key partition key; every event of one order shares it, which is what
     *            keeps their order guaranteed
     * @throws RuntimeException if the broker could not be reached
     */
    void publish(String topic, String key, String payload, Map<String, Object> headers);
}
