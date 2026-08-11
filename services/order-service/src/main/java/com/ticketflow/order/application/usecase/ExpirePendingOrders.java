package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.port.in.ExpirePendingOrdersUseCase;
import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.application.port.out.UnitOfWork;
import com.ticketflow.order.domain.exception.ConcurrentOrderUpdateException;
import com.ticketflow.order.domain.exception.TicketEventNotFoundException;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderItem;
import com.ticketflow.order.domain.model.TicketCategory;
import com.ticketflow.order.domain.model.TicketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Expires orders that were never paid and puts their tickets back on sale.
 *
 * <p>Each order is expired in its own transaction. One order failing - because a
 * payment result landed a moment earlier and moved it, say - must not abort the
 * whole sweep and leave the rest of the stale inventory locked away.
 *
 * <p>Losing a race here is expected, not exceptional: a {@code PAGAMENTO_APROVADO}
 * arriving while the sweep is running should win, and the optimistic version check
 * is what guarantees the order is not expired out from under a customer who did pay.
 */
public class ExpirePendingOrders implements ExpirePendingOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpirePendingOrders.class);

    private final OrderRepository orders;
    private final CatalogRepository catalog;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final int batchSize;

    public ExpirePendingOrders(OrderRepository orders,
                               CatalogRepository catalog,
                               UnitOfWork unitOfWork,
                               Clock clock,
                               int batchSize) {
        this.orders = Objects.requireNonNull(orders);
        this.catalog = Objects.requireNonNull(catalog);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
        this.batchSize = batchSize;
    }

    @Override
    public int execute() {
        Instant now = clock.instant();
        List<Order> stale = orders.findExpired(now, batchSize);

        int expired = 0;
        for (Order order : stale) {
            if (expireOne(order, now)) {
                expired++;
            }
        }

        if (expired > 0) {
            log.info("Expired {} order(s), releasing their reserved tickets", expired);
        }
        return expired;
    }

    private boolean expireOne(Order order, Instant now) {
        try {
            unitOfWork.executeVoid(() -> {
                order.expire(now);
                catalog.updateInventory(releasedCategories(order));
                orders.update(order);
            });
            return true;

        } catch (ConcurrentOrderUpdateException e) {
            // Someone else got there first - almost certainly the payment result.
            // Correct outcome: leave the order alone.
            log.debug("Order {} changed while expiring; skipping", order.id());
            return false;

        } catch (RuntimeException e) {
            // One bad order must not stop the sweep.
            log.warn("Could not expire order {}", order.id(), e);
            return false;
        }
    }

    private List<TicketCategory> releasedCategories(Order order) {
        TicketEvent ticketEvent = catalog.findById(order.ticketEventId())
                .orElseThrow(() -> new TicketEventNotFoundException(order.ticketEventId()));

        List<TicketCategory> released = new ArrayList<>(order.items().size());
        for (OrderItem item : order.items()) {
            TicketCategory category = ticketEvent.requireCategory(item.ticketCategoryId());
            category.releaseReservation(item.quantity());
            released.add(category);
        }
        return released;
    }
}
