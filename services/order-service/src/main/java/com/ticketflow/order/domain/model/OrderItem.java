package com.ticketflow.order.domain.model;

import com.ticketflow.order.domain.exception.InvalidOrderException;

import java.util.Objects;
import java.util.UUID;

/**
 * One line of an order: how many tickets of one category, at the price they cost
 * <em>when the order was placed</em>.
 *
 * <p>{@code unitPrice} and {@code categoryName} are copies, not lookups. If the
 * organiser raises the price or renames the tier tomorrow, an order placed today
 * must still show what the customer actually agreed to pay.
 */
public record OrderItem(UUID id,
                        UUID ticketCategoryId,
                        String categoryName,
                        int quantity,
                        Money unitPrice,
                        Money subtotal) {

    /** Matches the CHECK constraint on order_items and maxItems in the OpenAPI contract. */
    public static final int MAX_QUANTITY_PER_ITEM = 10;

    public OrderItem {
        Objects.requireNonNull(id, "order item id is required");
        Objects.requireNonNull(ticketCategoryId, "ticket category id is required");
        Objects.requireNonNull(categoryName, "category name is required");
        Objects.requireNonNull(unitPrice, "unit price is required");
        Objects.requireNonNull(subtotal, "subtotal is required");

        if (quantity < 1 || quantity > MAX_QUANTITY_PER_ITEM) {
            throw InvalidOrderException.invalidQuantity(MAX_QUANTITY_PER_ITEM, quantity);
        }
        if (!subtotal.equals(unitPrice.times(quantity))) {
            throw new IllegalArgumentException(
                    "subtotal %s does not match %s x %d".formatted(subtotal, unitPrice, quantity));
        }
    }

    /** Builds a line from the catalogue, copying the price as it stands right now. */
    public static OrderItem of(TicketCategory category, int quantity) {
        Objects.requireNonNull(category, "category is required");
        if (quantity < 1 || quantity > MAX_QUANTITY_PER_ITEM) {
            throw InvalidOrderException.invalidQuantity(MAX_QUANTITY_PER_ITEM, quantity);
        }
        return new OrderItem(
                UUID.randomUUID(),
                category.id(),
                category.name(),
                quantity,
                category.price(),
                category.price().times(quantity));
    }
}
