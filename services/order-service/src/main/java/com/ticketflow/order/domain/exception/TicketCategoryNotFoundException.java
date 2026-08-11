package com.ticketflow.order.domain.exception;

import java.util.UUID;

/**
 * Raised both when the category does not exist at all and when it exists but
 * belongs to a different event. The two are reported the same way on purpose:
 * telling a caller "that category exists, just not here" leaks the catalogue of
 * other events to anyone probing ids.
 */
public class TicketCategoryNotFoundException extends DomainException {

    public TicketCategoryNotFoundException(UUID ticketCategoryId, UUID ticketEventId) {
        super("ticket-category-not-found",
                "Ticket category %s does not belong to event %s.".formatted(ticketCategoryId, ticketEventId));
    }
}
