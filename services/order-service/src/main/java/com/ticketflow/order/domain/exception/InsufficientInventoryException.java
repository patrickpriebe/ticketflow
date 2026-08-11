package com.ticketflow.order.domain.exception;

public class InsufficientInventoryException extends DomainException {

    private final int requested;
    private final int available;

    public InsufficientInventoryException(String categoryName, int requested, int available) {
        super("insufficient-inventory",
                "Ticket category '%s' has only %d ticket(s) left, %d were requested."
                        .formatted(categoryName, available, requested));
        this.requested = requested;
        this.available = available;
    }

    public int requested() {
        return requested;
    }

    public int available() {
        return available;
    }
}
