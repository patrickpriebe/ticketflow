package com.ticketflow.order.domain.exception;

/**
 * Two customers reached for the same last tickets and this request lost the race.
 *
 * <p>Raised by the persistence adapter when optimistic locking on
 * {@code ticket_categories.version} fails. The caller is told to try again rather
 * than being served a silently oversold order.
 */
public class ConcurrentInventoryUpdateException extends DomainException {

    public ConcurrentInventoryUpdateException() {
        super("concurrent-inventory-update",
                "Those tickets were being bought by someone else. Please try again.");
    }
}
