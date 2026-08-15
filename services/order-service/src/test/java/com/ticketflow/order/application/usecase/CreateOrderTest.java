package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.port.in.CreateOrderUseCase;
import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.application.port.out.DomainEventPublisher;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.application.port.out.UnitOfWork;
import com.ticketflow.order.domain.CatalogFixtures;
import com.ticketflow.order.domain.event.OrderCreated;
import com.ticketflow.order.domain.exception.DuplicateIdempotencyKeyException;
import com.ticketflow.order.domain.exception.EventNotOnSaleException;
import com.ticketflow.order.domain.exception.InsufficientInventoryException;
import com.ticketflow.order.domain.exception.TicketCategoryNotFoundException;
import com.ticketflow.order.domain.exception.TicketEventNotFoundException;
import com.ticketflow.order.domain.model.Customer;
import com.ticketflow.order.domain.model.Money;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderTest {

    private static final String IDEMPOTENCY_KEY = "8a1f0c2e-6b3d-4a91-9f77-1c2d3e4f5a6b";
    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);

    @Mock
    private OrderRepository orders;
    @Mock
    private CatalogRepository catalog;
    @Mock
    private DomainEventPublisher eventPublisher;

    /**
     * A real implementation rather than a mock: it runs the block immediately, which
     * is all a unit test needs, and it is the reason this use case is testable
     * without a Spring context or a database.
     */
    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    };

    private CreateOrder createOrder;
    private Customer customer;

    @BeforeEach
    void setUp() {
        createOrder = new CreateOrder(
                orders,
                catalog,
                eventPublisher,
                unitOfWork,
                Clock.fixed(NOW, ZoneOffset.UTC),
                PAYMENT_WINDOW);
        customer = CatalogFixtures.customer();
    }

    private CreateOrderUseCase.Command command(TicketEvent event, UUID categoryId, int quantity) {
        return new CreateOrderUseCase.Command(
                IDEMPOTENCY_KEY,
                customer,
                event.id(),
                PaymentMethod.CREDIT_CARD,
                List.of(new RequestedItem(categoryId, quantity)));
    }

    private void givenCatalogHas(TicketEvent event) {
        when(catalog.findById(event.id())).thenReturn(Optional.of(event));
    }

    private void givenSaveEchoesBack() {
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("accepts the order as PENDING without contacting anyone about payment")
    void placesPendingOrder() {
        TicketEvent event = CatalogFixtures.onSaleEvent();
        TicketCategory pista = event.categories().get(0);
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        givenCatalogHas(event);
        givenSaveEchoesBack();

        CreateOrderUseCase.Result result = createOrder.execute(command(event, pista.id(), 2));

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.order().totalAmount()).isEqualTo(Money.of("1300.00", "BRL"));
        assertThat(result.order().expiresAt()).isEqualTo(NOW.plus(PAYMENT_WINDOW));
    }

    @Test
    @DisplayName("records ORDER_CREATED carrying the order and the event name")
    void publishesOrderCreated() {
        TicketEvent event = CatalogFixtures.onSaleEvent();
        TicketCategory pista = event.categories().get(0);
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        givenCatalogHas(event);
        givenSaveEchoesBack();

        createOrder.execute(command(event, pista.id(), 1));

        ArgumentCaptor<OrderCreated> published = ArgumentCaptor.forClass(OrderCreated.class);
        verify(eventPublisher).publish(published.capture());

        OrderCreated published0 = published.getValue();
        assertThat(published0.ticketEventName()).isEqualTo(event.name());
        assertThat(published0.occurredAt()).isEqualTo(NOW);
        assertThat(published0.eventId()).isNotNull();
        assertThat(published0.order().status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("persists the inventory it reserved")
    void persistsReservedInventory() {
        TicketEvent event = CatalogFixtures.onSaleEvent();
        TicketCategory pista = event.categories().get(0);
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        givenCatalogHas(event);
        givenSaveEchoesBack();

        createOrder.execute(command(event, pista.id(), 3));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TicketCategory>> updated = ArgumentCaptor.forClass(List.class);
        verify(catalog).updateInventory(updated.capture());

        assertThat(updated.getValue()).singleElement().satisfies(category ->
                assertThat(category.reservedQuantity()).isEqualTo(3));
    }

    @Test
    @DisplayName("a retry with the same Idempotency-Key returns the original order")
    void replaysKnownIdempotencyKey() {
        TicketEvent event = CatalogFixtures.onSaleEvent();
        TicketCategory pista = event.categories().get(0);
        Order original = Order.place(IDEMPOTENCY_KEY, customer, event, PaymentMethod.CREDIT_CARD,
                List.of(new RequestedItem(pista.id(), 1)), NOW, PAYMENT_WINDOW);
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.of(original));

        CreateOrderUseCase.Result result = createOrder.execute(command(event, pista.id(), 1));

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(original.id());

        // A network retry must not charge the customer twice: nothing new is written,
        // and no second ORDER_CREATED reaches the outbox.
        verify(orders, never()).save(any());
        verifyNoInteractions(eventPublisher);
        verify(catalog, never()).updateInventory(anyList());
    }

    @Test
    @DisplayName("the same key from a different customer is a new order, never a replay of someone else's")
    void idempotencyKeyIsScopedToTheCustomer() {
        // O vazamento que este teste fecha: a chave era procurada solta, então
        // mandar `Idempotency-Key: order-1` devolvia 200 com o pedido de quem
        // tivesse usado esse valor antes — nome, e-mail e o que a pessoa comprou.
        // Nenhum token alheio, nenhuma sondagem de id: só um cabeçalho comum.
        TicketEvent event = CatalogFixtures.onSaleEvent();
        TicketCategory pista = event.categories().get(0);
        Customer stranger = new Customer(UUID.randomUUID(), "Bruno Lima", "bruno.lima@example.com");
        Order theirOrder = Order.place(IDEMPOTENCY_KEY, stranger, event, PaymentMethod.CREDIT_CARD,
                List.of(new RequestedItem(pista.id(), 1)), NOW, PAYMENT_WINDOW);

        // A busca é sempre dentro de quem chama. Para este cliente a chave é nova,
        // ainda que o outro já a tenha gasto.
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        givenCatalogHas(event);
        givenSaveEchoesBack();

        CreateOrderUseCase.Result result = createOrder.execute(command(event, pista.id(), 1));

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().id()).isNotEqualTo(theirOrder.id());
        assertThat(result.order().customer().id()).isEqualTo(customer.id());
        // E, principalmente: nada do outro cliente saiu daqui.
        assertThat(result.order().customer().email()).isNotEqualTo(stranger.email());
    }

    @Test
    @DisplayName("when a concurrent request wins the race, returns that order instead of failing")
    void losesIdempotencyRaceGracefully() {
        TicketEvent event = CatalogFixtures.onSaleEvent();
        TicketCategory pista = event.categories().get(0);
        Order winner = Order.place(IDEMPOTENCY_KEY, customer, event, PaymentMethod.CREDIT_CARD,
                List.of(new RequestedItem(pista.id(), 1)), NOW, PAYMENT_WINDOW);

        // First lookup finds nothing; the insert then loses to the unique constraint;
        // the second lookup finds the winner. This is the window that makes a
        // check-then-insert idempotency scheme wrong on its own.
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        givenCatalogHas(event);
        when(orders.save(any(Order.class)))
                .thenThrow(new DuplicateIdempotencyKeyException(IDEMPOTENCY_KEY));

        CreateOrderUseCase.Result result = createOrder.execute(command(event, pista.id(), 1));

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(winner.id());
    }

    @Test
    @DisplayName("rethrows if the duplicate key cannot be resolved to an order")
    void unresolvableDuplicateIsRethrown() {
        TicketEvent event = CatalogFixtures.onSaleEvent();
        TicketCategory pista = event.categories().get(0);
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        givenCatalogHas(event);
        when(orders.save(any(Order.class)))
                .thenThrow(new DuplicateIdempotencyKeyException(IDEMPOTENCY_KEY));

        assertThatThrownBy(() -> createOrder.execute(command(event, pista.id(), 1)))
                .isInstanceOf(DuplicateIdempotencyKeyException.class);
    }

    @Test
    @DisplayName("refuses an unknown event and writes nothing")
    void unknownEvent() {
        UUID missingEventId = UUID.randomUUID();
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(catalog.findById(missingEventId)).thenReturn(Optional.empty());

        CreateOrderUseCase.Command command = new CreateOrderUseCase.Command(
                IDEMPOTENCY_KEY, customer, missingEventId, PaymentMethod.CREDIT_CARD,
                List.of(new RequestedItem(UUID.randomUUID(), 1)));

        assertThatThrownBy(() -> createOrder.execute(command))
                .isInstanceOf(TicketEventNotFoundException.class);

        verify(orders, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("refuses a category belonging to another event")
    void categoryFromAnotherEvent() {
        TicketEvent event = CatalogFixtures.onSaleEvent();
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        givenCatalogHas(event);

        assertThatThrownBy(() -> createOrder.execute(command(event, UUID.randomUUID(), 1)))
                .isInstanceOf(TicketCategoryNotFoundException.class);

        verify(orders, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("refuses an event whose sales window has closed")
    void salesWindowClosed() {
        TicketEvent event = CatalogFixtures.salesClosedEvent();
        TicketCategory plateia = event.categories().get(0);
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        givenCatalogHas(event);

        assertThatThrownBy(() -> createOrder.execute(command(event, plateia.id(), 1)))
                .isInstanceOf(EventNotOnSaleException.class);

        verify(orders, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("refuses to oversell and leaves no event behind")
    void insufficientInventory() {
        UUID eventId = UUID.randomUUID();
        TicketCategory almostGone = CatalogFixtures.category(
                UUID.randomUUID(), eventId, "Camarote", "2400.00", 2);
        TicketEvent event = CatalogFixtures.onSaleEvent(eventId, List.of(almostGone));
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        givenCatalogHas(event);

        assertThatThrownBy(() -> createOrder.execute(command(event, almostGone.id(), 4)))
                .isInstanceOf(InsufficientInventoryException.class);

        verify(orders, never()).save(any());
        verifyNoInteractions(eventPublisher);
        verify(catalog, never()).updateInventory(anyList());
    }

    @Test
    @DisplayName("saves the order exactly once")
    void savesOnce() {
        TicketEvent event = CatalogFixtures.onSaleEvent();
        TicketCategory pista = event.categories().get(0);
        when(orders.findByIdempotencyKey(customer.id(), IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        givenCatalogHas(event);
        givenSaveEchoesBack();

        createOrder.execute(command(event, pista.id(), 1));

        verify(orders, times(1)).save(any(Order.class));
        verify(eventPublisher, times(1)).publish(any(OrderCreated.class));
    }
}
