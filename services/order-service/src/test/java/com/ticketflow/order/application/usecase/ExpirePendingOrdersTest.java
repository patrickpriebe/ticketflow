package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.application.port.out.UnitOfWork;
import com.ticketflow.order.domain.CatalogFixtures;
import com.ticketflow.order.domain.exception.ConcurrentOrderUpdateException;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderStatus;
import com.ticketflow.order.domain.model.PaymentMethod;
import com.ticketflow.order.domain.model.RequestedItem;
import com.ticketflow.order.domain.model.TicketCategory;
import com.ticketflow.order.domain.model.TicketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.ticketflow.order.domain.CatalogFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpirePendingOrdersTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Instant LATER = NOW.plus(WINDOW).plusSeconds(1);

    @Mock
    private OrderRepository orders;
    @Mock
    private CatalogRepository catalog;

    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    };

    private ExpirePendingOrders expirePendingOrders;
    private TicketEvent ticketEvent;
    private TicketCategory pista;

    @BeforeEach
    void setUp() {
        expirePendingOrders = new ExpirePendingOrders(
                orders, catalog, unitOfWork, Clock.fixed(LATER, ZoneOffset.UTC), 50);
        ticketEvent = CatalogFixtures.onSaleEvent();
        pista = ticketEvent.categories().get(0);
    }

    private Order staleOrder(int quantity) {
        return Order.place("idem-" + UUID.randomUUID(), CatalogFixtures.customer(), ticketEvent,
                PaymentMethod.CREDIT_CARD, List.of(new RequestedItem(pista.id(), quantity)),
                NOW, WINDOW);
    }

    @Test
    @DisplayName("expires a stale order and puts its tickets back on sale")
    void expiresAndReleases() {
        Order order = staleOrder(2);
        when(orders.findExpired(LATER, 50)).thenReturn(List.of(order));
        when(catalog.findById(ticketEvent.id())).thenReturn(Optional.of(ticketEvent));

        int expired = expirePendingOrders.execute();

        assertThat(expired).isEqualTo(1);
        assertThat(order.status()).isEqualTo(OrderStatus.EXPIRED);
        // The whole point: seats held for a customer who never paid go back on sale.
        assertThat(pista.reservedQuantity()).isZero();
        assertThat(pista.soldQuantity()).isZero();
        verify(orders).update(order);
    }

    @Test
    @DisplayName("records the reason on the order's timeline")
    void recordsReason() {
        Order order = staleOrder(1);
        when(orders.findExpired(LATER, 50)).thenReturn(List.of(order));
        when(catalog.findById(ticketEvent.id())).thenReturn(Optional.of(ticketEvent));

        expirePendingOrders.execute();

        assertThat(order.statusHistory()).last().satisfies(change -> {
            assertThat(change.toStatus()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(change.reason()).isEqualTo("Payment window elapsed");
        });
    }

    @Test
    @DisplayName("does nothing when there is nothing stale")
    void noopWhenNothingStale() {
        when(orders.findExpired(LATER, 50)).thenReturn(List.of());

        assertThat(expirePendingOrders.execute()).isZero();
        verify(orders, never()).update(any());
    }

    @Test
    @DisplayName("a payment that landed first wins the race and the order is left alone")
    void concurrentPaymentWins() {
        Order order = staleOrder(2);
        when(orders.findExpired(LATER, 50)).thenReturn(List.of(order));
        when(catalog.findById(ticketEvent.id())).thenReturn(Optional.of(ticketEvent));
        doThrow(new ConcurrentOrderUpdateException(order.id())).when(orders).update(order);

        int expired = expirePendingOrders.execute();

        // Expiring an order the customer actually paid for would be far worse than
        // leaving stale inventory around for one more sweep.
        assertThat(expired).isZero();
    }

    @Test
    @DisplayName("one bad order does not abort the sweep")
    void keepsGoingAfterFailure() {
        Order broken = staleOrder(1);
        Order healthy = staleOrder(1);
        when(orders.findExpired(LATER, 50)).thenReturn(List.of(broken, healthy));
        when(catalog.findById(ticketEvent.id())).thenReturn(Optional.of(ticketEvent));
        doThrow(new IllegalStateException("boom")).when(orders).update(broken);

        int expired = expirePendingOrders.execute();

        assertThat(expired).isEqualTo(1);
        assertThat(healthy.status()).isEqualTo(OrderStatus.EXPIRED);
        verify(orders, times(2)).update(any());
    }

    @Test
    @DisplayName("asks the repository only for orders already past their deadline")
    void queriesByDeadline() {
        when(orders.findExpired(any(), anyInt())).thenReturn(List.of());

        expirePendingOrders.execute();

        // The filtering is the database's job, not a full scan filtered in memory.
        verify(orders).findExpired(LATER, 50);
        verify(catalog, never()).updateInventory(anyList());
    }
}
