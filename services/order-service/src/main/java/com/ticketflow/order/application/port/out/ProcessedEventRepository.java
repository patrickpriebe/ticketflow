package com.ticketflow.order.application.port.out;

import java.util.UUID;

/**
 * Driven port: the inbox that makes consuming idempotent.
 *
 * <p>Kafka delivers at-least-once, so the same event will eventually arrive twice.
 * Every consumer asks here before acting and records the id afterwards, in the same
 * transaction as the business change - otherwise a crash in between would either
 * lose the work or repeat it.
 *
 * <p>The consumer group is deliberately absent from this interface: it is a
 * messaging concept, and the adapter supplies it from configuration. The
 * application layer only knows "have I already handled this event?".
 */
public interface ProcessedEventRepository {

    boolean alreadyProcessed(UUID eventId);

    void record(UUID eventId);
}
