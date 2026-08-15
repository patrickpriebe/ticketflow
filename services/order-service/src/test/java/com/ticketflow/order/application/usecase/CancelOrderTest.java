package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.application.port.out.DomainEventPublisher;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.application.port.out.UnitOfWork;
import com.ticketflow.order.domain.CatalogFixtures;
import com.ticketflow.order.domain.event.OrderCancelled;
import com.ticketflow.order.domain.exception.InvalidOrderStatusTransitionException;
import com.ticketflow.order.domain.exception.OrderNotFoundException;
import com.ticketflow.order.domain.model.Customer;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static com.ticketflow.order.domain.CatalogFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelOrderTest {

    @Mock
    private OrderRepository orders;
    @Mock
    private CatalogRepository catalog;
    @Mock
    private DomainEventPublisher eventPublisher;

    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    };

    private CancelOrder cancelOrder;
    private TicketEvent ticketEvent;
    private TicketCategory pista;
    private Customer owner;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        cancelOrder = new CancelOrder(orders, catalog, eventPublisher, unitOfWork,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));

        ticketEvent = CatalogFixtures.onSaleEvent();
        pista = ticketEvent.categories().get(0);
        owner = CatalogFixtures.customer();
        pendingOrder = Order.place("idem-1", owner, ticketEvent, PaymentMethod.CREDIT_CARD,
                List.of(new RequestedItem(pista.id(), 2)), NOW, Duration.ofMinutes(15));
    }

    private void givenOrderAndCatalogExist() {
        when(orders.findById(pendingOrder.id())).thenReturn(Optional.of(pendingOrder));
        when(catalog.findById(ticketEvent.id())).thenReturn(Optional.of(ticketEvent));
    }

    @Test
    @DisplayName("cancelling puts the reserved tickets back on sale")
    void releasesReservation() {
        givenOrderAndCatalogExist();
        int availableBefore = pista.availableQuantity();

        Order cancelled = cancelOrder.execute(pendingOrder.id(), owner.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(pista.reservedQuantity()).isZero();
        assertThat(pista.soldQuantity()).isZero();
        assertThat(pista.availableQuantity()).isEqualTo(availableBefore + 2);
        verify(orders).update(pendingOrder);
    }

    @Test
    @DisplayName("publishes ORDER_CANCELLED carrying the amount, so the refunder needs nobody")
    void publishesCompensationTrigger() {
        givenOrderAndCatalogExist();

        cancelOrder.execute(pendingOrder.id(), owner.id());

        ArgumentCaptor<OrderCancelled> published = ArgumentCaptor.forClass(OrderCancelled.class);
        verify(eventPublisher).publish(published.capture());
        assertThat(published.getValue().order()).isSameAs(pendingOrder);
        assertThat(published.getValue().order().totalAmount().amount())
                .isEqualByComparingTo("1300.00");
        // A hora do cancelamento, não a do relógio de quem publicar depois.
        assertThat(published.getValue().occurredAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    @DisplayName("someone else's order answers exactly like an order that does not exist")
    void anotherCustomersOrder() {
        // A 403 aqui confirmaria que o pedido existe para quem estivesse sondando ids.
        when(orders.findById(pendingOrder.id())).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> cancelOrder.execute(pendingOrder.id(), UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);

        assertThat(pendingOrder.status()).isEqualTo(OrderStatus.PENDING);
        // O estoque continua reservado: ninguém libera o pedido de outra pessoa.
        assertThat(pista.reservedQuantity()).isEqualTo(2);
        verify(orders, never()).update(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("an unknown order fails the same way")
    void unknownOrder() {
        UUID missing = UUID.randomUUID();
        when(orders.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancelOrder.execute(missing, owner.id()))
                .isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("a paid order cannot be cancelled - that is a refund, another flow")
    void alreadyPaidOrder() {
        pendingOrder.markPaid(UUID.randomUUID(), NOW.plusSeconds(10));
        when(orders.findById(pendingOrder.id())).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> cancelOrder.execute(pendingOrder.id(), owner.id()))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        // Nada de estoque devolvido: aquelas entradas foram vendidas.
        verify(orders, never()).update(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("cancelling twice fails instead of releasing the same tickets again")
    void alreadyCancelledOrder() {
        // Sem isto, dois cliques no botão devolveriam 4 entradas de uma reserva de 2
        // e o estoque passaria a vender mais do que existe.
        pendingOrder.cancel("Cancelled by the customer", NOW.plusSeconds(10));
        when(orders.findById(pendingOrder.id())).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> cancelOrder.execute(pendingOrder.id(), owner.id()))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orders, never()).update(any());
        verifyNoInteractions(eventPublisher);
    }
}
