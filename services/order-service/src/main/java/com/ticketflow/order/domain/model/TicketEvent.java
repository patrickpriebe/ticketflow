package com.ticketflow.order.domain.model;

import com.ticketflow.order.domain.exception.EventNotOnSaleException;
import com.ticketflow.order.domain.exception.TicketCategoryNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A show, match or concert - the thing being sold.
 *
 * <p>Named {@code TicketEvent} rather than {@code Event} deliberately: this codebase
 * is full of Kafka events and Spring {@code ApplicationEvent}s, and a bare
 * {@code Event} would be ambiguous in every import list it appears in.
 */
public class TicketEvent {

    private final UUID id;
    private final String name;
    /** Free text shown on the event page. Optional: the catalogue allows it to be null. */
    private final String description;
    private final String venue;
    private final String city;
    private final Instant startsAt;
    private final Instant salesStartAt;
    private final Instant salesEndAt;
    private final EventStatus status;
    private final List<TicketCategory> categories;

    public TicketEvent(UUID id,
                       String name,
                       String description,
                       String venue,
                       String city,
                       Instant startsAt,
                       Instant salesStartAt,
                       Instant salesEndAt,
                       EventStatus status,
                       List<TicketCategory> categories) {
        this.id = Objects.requireNonNull(id, "event id is required");
        this.name = Objects.requireNonNull(name, "event name is required");
        this.description = description;
        this.venue = Objects.requireNonNull(venue, "venue is required");
        this.city = Objects.requireNonNull(city, "city is required");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt is required");
        this.salesStartAt = Objects.requireNonNull(salesStartAt, "salesStartAt is required");
        this.salesEndAt = Objects.requireNonNull(salesEndAt, "salesEndAt is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.categories = List.copyOf(Objects.requireNonNull(categories, "categories are required"));

        if (!salesEndAt.isAfter(salesStartAt)) {
            throw new IllegalArgumentException("salesEndAt must be after salesStartAt");
        }
    }

    /**
     * @throws EventNotOnSaleException if the event is not accepting orders right now
     */
    public void ensureOnSaleAt(Instant now) {
        Objects.requireNonNull(now, "now is required");
        if (!status.acceptsOrders()) {
            throw EventNotOnSaleException.wrongStatus(id, status.name());
        }
        if (now.isBefore(salesStartAt) || !now.isBefore(salesEndAt)) {
            throw EventNotOnSaleException.salesWindowClosed(id);
        }
    }

    public boolean isOnSaleAt(Instant now) {
        return status.acceptsOrders()
                && !now.isBefore(salesStartAt)
                && now.isBefore(salesEndAt);
    }

    /**
     * @throws TicketCategoryNotFoundException if the category does not belong to
     *                                         this event - which also covers the
     *                                         case of it not existing at all
     */
    public TicketCategory requireCategory(UUID ticketCategoryId) {
        Objects.requireNonNull(ticketCategoryId, "ticket category id is required");
        return categories.stream()
                .filter(category -> category.id().equals(ticketCategoryId))
                .findFirst()
                .orElseThrow(() -> new TicketCategoryNotFoundException(ticketCategoryId, id));
    }

    /** Cheapest category still available, for the "a partir de" price in listings. */
    public Money priceFrom() {
        return categories.stream()
                .filter(category -> !category.isSoldOut())
                .map(TicketCategory::price)
                .min(Money::compareTo)
                .orElse(null);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String venue() {
        return venue;
    }

    public String city() {
        return city;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public Instant salesStartAt() {
        return salesStartAt;
    }

    public Instant salesEndAt() {
        return salesEndAt;
    }

    public EventStatus status() {
        return status;
    }

    public List<TicketCategory> categories() {
        return categories;
    }
}
