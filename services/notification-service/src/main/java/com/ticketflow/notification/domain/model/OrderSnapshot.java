package com.ticketflow.notification.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What this service remembers about an order, built from ORDER_CREATED.
 *
 * <p>It exists because PAGAMENTO_APROVADO deliberately does not carry the basket:
 * it says how much was charged, not what was bought. Asking the Order Service would
 * mean a synchronous call between services, which is exactly what this architecture
 * forbids - so the service keeps its own read model instead, fed by the same event
 * stream everyone else reads.
 *
 * <p>Classic CQRS: the write side owns orders, this side owns a projection shaped
 * for what it has to do, which is print tickets.
 */
public record OrderSnapshot(String orderId,
                            String customerId,
                            String customerName,
                            String customerEmail,
                            String eventId,
                            String eventName,
                            List<Line> items,
                            Instant receivedAt) {

    public OrderSnapshot {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(customerEmail, "customerEmail is required");
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }

    /** @param quantity how many tickets of this category, so seats can be numbered */
    public record Line(String ticketCategoryId, String categoryName, int quantity) {
    }

    public int totalTickets() {
        return items.stream().mapToInt(Line::quantity).sum();
    }

    public Ticket.Holder holder() {
        return new Ticket.Holder(customerId, customerName, customerEmail);
    }
}
