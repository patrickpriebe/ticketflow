package com.ticketflow.order.domain.exception;

import java.util.UUID;

public class InvalidOrderException extends DomainException {

    public static InvalidOrderException noItems() {
        return new InvalidOrderException("An order must contain at least one item.");
    }

    public static InvalidOrderException tooManyItems(int max, int actual) {
        return new InvalidOrderException(
                "An order may contain at most %d item(s), got %d.".formatted(max, actual));
    }

    public static InvalidOrderException duplicateCategory(UUID ticketCategoryId) {
        return new InvalidOrderException(
                ("Ticket category %s appears more than once. Send one line per category "
                        + "with the total quantity instead.").formatted(ticketCategoryId));
    }

    public static InvalidOrderException invalidQuantity(int max, int actual) {
        return new InvalidOrderException(
                "Quantity must be between 1 and %d, got %d.".formatted(max, actual));
    }

    private InvalidOrderException(String message) {
        super("invalid-order", message);
    }
}
