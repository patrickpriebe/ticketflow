package com.ticketflow.order.domain.exception;

import java.util.UUID;

public class EventNotOnSaleException extends DomainException {

    public static EventNotOnSaleException wrongStatus(UUID ticketEventId, String status) {
        return new EventNotOnSaleException(
                "Event %s is not on sale (status %s).".formatted(ticketEventId, status));
    }

    public static EventNotOnSaleException salesWindowClosed(UUID ticketEventId) {
        return new EventNotOnSaleException(
                "Event %s is outside its sales window.".formatted(ticketEventId));
    }

    private EventNotOnSaleException(String message) {
        super("event-not-on-sale", message);
    }
}
