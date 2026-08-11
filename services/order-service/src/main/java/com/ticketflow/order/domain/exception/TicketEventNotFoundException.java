package com.ticketflow.order.domain.exception;

import java.util.UUID;

public class TicketEventNotFoundException extends DomainException {

    public TicketEventNotFoundException(UUID ticketEventId) {
        super("event-not-found", "Event %s does not exist.".formatted(ticketEventId));
    }
}
