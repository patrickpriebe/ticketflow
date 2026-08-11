package com.ticketflow.order.domain.model;

import com.ticketflow.order.domain.exception.InsufficientInventoryException;

import java.util.Objects;
import java.util.UUID;

/**
 * A price tier within an event - Pista, VIP, Camarote.
 *
 * <p>Inventory lives here, and so does the rule that protects it. {@link #reserve}
 * is the only way the counters move, which means "never sell more tickets than
 * exist" is enforced in one place instead of being re-checked by every caller.
 *
 * <p>Two customers can still race for the last ticket. That is handled one layer
 * out, by the {@code version} column and JPA optimistic locking: both read the same
 * state, both reserve, and the second write loses. The database also carries a
 * CHECK constraint as a final backstop.
 */
public class TicketCategory {

    private final UUID id;
    private final UUID ticketEventId;
    private final String name;
    private final Money price;
    private final int totalQuantity;
    private int reservedQuantity;
    private int soldQuantity;
    private final long version;

    public TicketCategory(UUID id,
                          UUID ticketEventId,
                          String name,
                          Money price,
                          int totalQuantity,
                          int reservedQuantity,
                          int soldQuantity,
                          long version) {
        this.id = Objects.requireNonNull(id, "ticket category id is required");
        this.ticketEventId = Objects.requireNonNull(ticketEventId, "ticket event id is required");
        this.name = Objects.requireNonNull(name, "ticket category name is required");
        this.price = Objects.requireNonNull(price, "price is required");
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("totalQuantity must be positive, got: " + totalQuantity);
        }
        if (reservedQuantity < 0 || soldQuantity < 0) {
            throw new IllegalArgumentException("inventory counters cannot be negative");
        }
        if (reservedQuantity + soldQuantity > totalQuantity) {
            throw new IllegalArgumentException(
                    "reserved + sold cannot exceed total for category " + name);
        }
        this.totalQuantity = totalQuantity;
        this.reservedQuantity = reservedQuantity;
        this.soldQuantity = soldQuantity;
        this.version = version;
    }

    public int availableQuantity() {
        return totalQuantity - reservedQuantity - soldQuantity;
    }

    public boolean isSoldOut() {
        return availableQuantity() == 0;
    }

    /**
     * Checks availability without touching the counters.
     *
     * <p>Exists so a caller building a multi-line order can validate every line
     * before reserving any of them. {@link #reserve} delegates here, so the rule
     * itself is still written once.
     *
     * @throws InsufficientInventoryException if there are not that many left
     */
    public void ensureAvailable(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got: " + quantity);
        }
        if (quantity > availableQuantity()) {
            throw new InsufficientInventoryException(name, quantity, availableQuantity());
        }
    }

    /**
     * Holds {@code quantity} tickets for an order that has not been paid yet.
     *
     * @throws InsufficientInventoryException if there are not that many left
     */
    public void reserve(int quantity) {
        ensureAvailable(quantity);
        reservedQuantity += quantity;
    }

    /** Releases a reservation - the payment was refused, or the order expired. */
    public void releaseReservation(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity to release must be positive, got: " + quantity);
        }
        if (quantity > reservedQuantity) {
            throw new IllegalArgumentException(
                    "cannot release %d ticket(s): only %d are reserved".formatted(quantity, reservedQuantity));
        }
        reservedQuantity -= quantity;
    }

    /** Turns a reservation into a sale - the payment was approved. */
    public void confirmSale(int quantity) {
        releaseReservation(quantity);
        soldQuantity += quantity;
    }

    public UUID id() {
        return id;
    }

    public UUID ticketEventId() {
        return ticketEventId;
    }

    public String name() {
        return name;
    }

    public Money price() {
        return price;
    }

    public int totalQuantity() {
        return totalQuantity;
    }

    public int reservedQuantity() {
        return reservedQuantity;
    }

    public int soldQuantity() {
        return soldQuantity;
    }

    public long version() {
        return version;
    }
}
