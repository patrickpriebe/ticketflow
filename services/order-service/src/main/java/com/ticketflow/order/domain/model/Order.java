package com.ticketflow.order.domain.model;

import com.ticketflow.order.domain.exception.InvalidOrderException;
import com.ticketflow.order.domain.exception.InvalidOrderStatusTransitionException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The aggregate root: a customer's purchase.
 *
 * <p>An order is born {@link OrderStatus#PENDING} and stays that way until a payment
 * result arrives from Kafka. Nothing in this class knows that Kafka exists - it only
 * exposes the transitions, and the application layer decides what triggers them.
 */
public class Order {

    /** Distinct ticket categories per order. Matches maxItems in the OpenAPI contract. */
    public static final int MAX_ITEMS = 10;

    private final UUID id;
    private final String idempotencyKey;
    private final Customer customer;
    private final UUID ticketEventId;
    private final PaymentMethod paymentMethod;
    private final List<OrderItem> items;
    private final Money totalAmount;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final long version;

    private OrderStatus status;
    private Instant updatedAt;
    private final List<OrderStatusChange> statusHistory;

    private Order(UUID id,
                  String idempotencyKey,
                  Customer customer,
                  UUID ticketEventId,
                  OrderStatus status,
                  PaymentMethod paymentMethod,
                  List<OrderItem> items,
                  Money totalAmount,
                  Instant expiresAt,
                  Instant createdAt,
                  Instant updatedAt,
                  List<OrderStatusChange> statusHistory,
                  long version) {
        this.id = Objects.requireNonNull(id, "order id is required");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key is required");
        this.customer = Objects.requireNonNull(customer, "customer is required");
        this.ticketEventId = Objects.requireNonNull(ticketEventId, "ticket event id is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "payment method is required");
        this.items = new ArrayList<>(Objects.requireNonNull(items, "items are required"));
        this.totalAmount = Objects.requireNonNull(totalAmount, "total amount is required");
        this.expiresAt = expiresAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.statusHistory = new ArrayList<>(Objects.requireNonNull(statusHistory, "status history is required"));
        this.version = version;
    }

    /**
     * Places a new order, reserving inventory as it goes.
     *
     * <p>Prices come from {@code ticketEvent}, never from the request. The
     * reservation happens in a second pass, after every requested line has been
     * validated, so a failure on the last line cannot leave the first ones
     * half-reserved.
     *
     * @throws com.ticketflow.order.domain.exception.EventNotOnSaleException        event closed for sales
     * @throws com.ticketflow.order.domain.exception.TicketCategoryNotFoundException category not in this event
     * @throws com.ticketflow.order.domain.exception.InsufficientInventoryException  not enough tickets left
     * @throws InvalidOrderException                                                empty, oversized or duplicated lines
     */
    public static Order place(String idempotencyKey,
                              Customer customer,
                              TicketEvent ticketEvent,
                              PaymentMethod paymentMethod,
                              List<RequestedItem> requestedItems,
                              Instant now,
                              Duration paymentWindow) {
        Objects.requireNonNull(ticketEvent, "ticket event is required");
        Objects.requireNonNull(requestedItems, "requested items are required");
        Objects.requireNonNull(now, "now is required");
        Objects.requireNonNull(paymentWindow, "payment window is required");

        if (requestedItems.isEmpty()) {
            throw InvalidOrderException.noItems();
        }
        if (requestedItems.size() > MAX_ITEMS) {
            throw InvalidOrderException.tooManyItems(MAX_ITEMS, requestedItems.size());
        }

        ticketEvent.ensureOnSaleAt(now);

        // Pass 1: resolve and validate everything, mutating nothing.
        Set<UUID> seen = new HashSet<>();
        Map<TicketCategory, Integer> resolved = new LinkedHashMap<>();
        for (RequestedItem requested : requestedItems) {
            if (!seen.add(requested.ticketCategoryId())) {
                throw InvalidOrderException.duplicateCategory(requested.ticketCategoryId());
            }
            if (requested.quantity() < 1 || requested.quantity() > OrderItem.MAX_QUANTITY_PER_ITEM) {
                throw InvalidOrderException.invalidQuantity(
                        OrderItem.MAX_QUANTITY_PER_ITEM, requested.quantity());
            }
            TicketCategory category = ticketEvent.requireCategory(requested.ticketCategoryId());
            // Availability is checked here, not only inside reserve(): otherwise the
            // first line would already be held when a later line turns out to be
            // sold out, locking tickets away for an order that was never created.
            category.ensureAvailable(requested.quantity());
            resolved.put(category, requested.quantity());
        }

        // Pass 2: reserve and build the lines.
        List<OrderItem> items = new ArrayList<>(resolved.size());
        Money total = null;
        for (Map.Entry<TicketCategory, Integer> entry : resolved.entrySet()) {
            TicketCategory category = entry.getKey();
            int quantity = entry.getValue();

            category.reserve(quantity);
            OrderItem item = OrderItem.of(category, quantity);
            items.add(item);
            total = (total == null) ? item.subtotal() : total.plus(item.subtotal());
        }

        return new Order(
                UUID.randomUUID(),
                idempotencyKey,
                customer,
                ticketEvent.id(),
                OrderStatus.PENDING,
                paymentMethod,
                items,
                total,
                now.plus(paymentWindow),
                now,
                now,
                List.of(OrderStatusChange.initial(now)),
                0L);
    }

    /** Rebuilds an order already stored in the database. Applies no business rules. */
    public static Order restore(UUID id,
                                String idempotencyKey,
                                Customer customer,
                                UUID ticketEventId,
                                OrderStatus status,
                                PaymentMethod paymentMethod,
                                List<OrderItem> items,
                                Money totalAmount,
                                Instant expiresAt,
                                Instant createdAt,
                                Instant updatedAt,
                                List<OrderStatusChange> statusHistory,
                                long version) {
        return new Order(id, idempotencyKey, customer, ticketEventId, status, paymentMethod,
                items, totalAmount, expiresAt, createdAt, updatedAt, statusHistory, version);
    }

    public void markPaid(UUID sourceEventId, Instant occurredAt) {
        transitionTo(OrderStatus.PAID, "Payment approved", sourceEventId, occurredAt);
    }

    public void markRejected(String reason, UUID sourceEventId, Instant occurredAt) {
        transitionTo(OrderStatus.REJECTED, reason, sourceEventId, occurredAt);
    }

    public void cancel(String reason, Instant occurredAt) {
        transitionTo(OrderStatus.CANCELLED, reason, null, occurredAt);
    }

    public void expire(Instant occurredAt) {
        transitionTo(OrderStatus.EXPIRED, "Payment window elapsed", null, occurredAt);
    }

    private void transitionTo(OrderStatus target, String reason, UUID sourceEventId, Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderStatusTransitionException(status, target);
        }
        statusHistory.add(new OrderStatusChange(status, target, reason, sourceEventId, occurredAt));
        status = target;
        updatedAt = occurredAt;
    }

    public boolean isExpiredAt(Instant now) {
        return status == OrderStatus.PENDING && expiresAt != null && !now.isBefore(expiresAt);
    }

    public UUID id() {
        return id;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Customer customer() {
        return customer;
    }

    public UUID ticketEventId() {
        return ticketEventId;
    }

    public OrderStatus status() {
        return status;
    }

    public PaymentMethod paymentMethod() {
        return paymentMethod;
    }

    public List<OrderItem> items() {
        return List.copyOf(items);
    }

    public Money totalAmount() {
        return totalAmount;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<OrderStatusChange> statusHistory() {
        return List.copyOf(statusHistory);
    }

    public long version() {
        return version;
    }
}
