package com.ticketflow.payment.application.port.out;

import java.util.UUID;

/**
 * Driven port: the inbox that makes consuming ORDER_CREATED idempotent.
 *
 * <p>The consumer group is supplied by the adapter from configuration - the
 * application layer only asks "have I already handled this event?".
 */
public interface ProcessedEventRepository {

    boolean alreadyProcessed(UUID eventId);

    void record(UUID eventId);
}
