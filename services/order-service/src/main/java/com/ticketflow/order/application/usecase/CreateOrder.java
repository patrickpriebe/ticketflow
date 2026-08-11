package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.port.in.CreateOrderUseCase;
import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.application.port.out.DomainEventPublisher;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.application.port.out.UnitOfWork;
import com.ticketflow.order.domain.event.OrderCreated;
import com.ticketflow.order.domain.exception.DuplicateIdempotencyKeyException;
import com.ticketflow.order.domain.exception.TicketEventNotFoundException;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderItem;
import com.ticketflow.order.domain.model.TicketCategory;
import com.ticketflow.order.domain.model.TicketEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Places an order and records the ORDER_CREATED event, atomically.
 *
 * <p>The whole point of the service lives in {@link #execute}: it never contacts the
 * Payment Service. It writes the order and the event, commits, and returns. The
 * money is somebody else's problem, moments later, over Kafka.
 *
 * <p>No Spring annotations here by design - this class is instantiated by
 * {@code UseCaseConfiguration} and unit-tested with plain fakes.
 */
public class CreateOrder implements CreateOrderUseCase {

    private final OrderRepository orders;
    private final CatalogRepository catalog;
    private final DomainEventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final Duration paymentWindow;

    public CreateOrder(OrderRepository orders,
                       CatalogRepository catalog,
                       DomainEventPublisher eventPublisher,
                       UnitOfWork unitOfWork,
                       Clock clock,
                       Duration paymentWindow) {
        this.orders = Objects.requireNonNull(orders);
        this.catalog = Objects.requireNonNull(catalog);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
        this.paymentWindow = Objects.requireNonNull(paymentWindow);
    }

    @Override
    public Result execute(Command command) {
        Objects.requireNonNull(command, "command is required");

        // Cheap path: this key was already used, so there is nothing to do.
        Optional<Order> alreadyPlaced = orders.findByIdempotencyKey(command.idempotencyKey());
        if (alreadyPlaced.isPresent()) {
            return Result.replayed(alreadyPlaced.get());
        }

        try {
            return unitOfWork.execute(() -> placeOrder(command));
        } catch (DuplicateIdempotencyKeyException duplicate) {
            // A concurrent request with the same key committed first. The unique
            // constraint caught it - which is why the check above is an optimisation,
            // not the actual guarantee. Return that order instead of failing.
            return orders.findByIdempotencyKey(command.idempotencyKey())
                    .map(Result::replayed)
                    .orElseThrow(() -> duplicate);
        }
    }

    private Result placeOrder(Command command) {
        Instant now = clock.instant();
        TicketEvent ticketEvent = catalog.findById(command.ticketEventId())
                .orElseThrow(() -> new TicketEventNotFoundException(command.ticketEventId()));

        // Reserves inventory on the in-memory categories and enforces every rule.
        Order order = Order.place(
                command.idempotencyKey(),
                command.customer(),
                ticketEvent,
                command.paymentMethod(),
                command.items(),
                now,
                paymentWindow);

        catalog.updateInventory(reservedCategories(ticketEvent, order));
        Order saved = orders.save(order);

        // Goes to the outbox, in this same transaction - not to Kafka.
        eventPublisher.publish(OrderCreated.of(saved, ticketEvent.name(), now));

        return Result.created(saved);
    }

    private List<TicketCategory> reservedCategories(TicketEvent ticketEvent, Order order) {
        return order.items().stream()
                .map(OrderItem::ticketCategoryId)
                .map(ticketEvent::requireCategory)
                .toList();
    }
}
