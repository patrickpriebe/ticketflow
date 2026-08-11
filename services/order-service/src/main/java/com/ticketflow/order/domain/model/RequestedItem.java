package com.ticketflow.order.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * What the customer asked for, before the catalogue has been consulted.
 *
 * <p>Carries no price: the price is never taken from the request, always from the
 * catalogue. Trusting a client-supplied price would let anyone buy a camarote
 * ticket for one real.
 */
public record RequestedItem(UUID ticketCategoryId, int quantity) {

    public RequestedItem {
        Objects.requireNonNull(ticketCategoryId, "ticket category id is required");
    }
}
