package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.port.in.ApplyPaymentResultUseCase;
import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.application.port.out.ProcessedEventRepository;
import com.ticketflow.order.application.port.out.UnitOfWork;
import com.ticketflow.order.domain.CatalogFixtures;
import com.ticketflow.order.domain.exception.InvalidOrderStatusTransitionException;
import com.ticketflow.order.domain.exception.OrderNotFoundException;
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

import java.time.Duration;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyPaymentResultTest {

    @Mock
    private OrderRepository orders;
    @Mock
    private CatalogRepository catalog;
    @Mock
    private ProcessedEventRepository processedEvents;

    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    };

    private ApplyPaymentResult applyPaymentResult;
    private TicketEvent ticketEvent;
    private TicketCategory pista;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        applyPaymentResult = new ApplyPaymentResult(orders, catalog, processedEvents, unitOfWork);

        ticketEvent = CatalogFixtures.onSaleEvent();
        pista = ticketEvent.categories().get(0);
        // Placing the order reserves the tickets, exactly as CreateOrder did before
        // the event went out.
        pendingOrder = Order.place("idem-1", CatalogFixtures.customer(), ticketEvent,
                PaymentMethod.CREDIT_CARD, List.of(new RequestedItem(pista.id(), 2)),
                NOW, Duration.ofMinutes(15));
    }

    private ApplyPaymentResultUseCase.Command approved() {
        return new ApplyPaymentResultUseCase.Command(
                UUID.randomUUID(), pendingOrder.id(), true, null, NOW.plusSeconds(3));
    }

    private ApplyPaymentResultUseCase.Command rejected() {
        return new ApplyPaymentResultUseCase.Command(
                UUID.randomUUID(), pendingOrder.id(), false, "Card declined by issuer", NOW.plusSeconds(3));
    }

    private void givenOrderAndCatalogExist() {
        when(orders.findById(pendingOrder.id())).thenReturn(Optional.of(pendingOrder));
        when(catalog.findById(ticketEvent.id())).thenReturn(Optional.of(ticketEvent));
    }

    @Test
    @DisplayName("approval turns the order PAID and the reservation into a sale")
    void approvalConfirmsSale() {
        givenOrderAndCatalogExist();

        ApplyPaymentResultUseCase.Result result = applyPaymentResult.execute(approved());

        assertThat(result).isEqualTo(ApplyPaymentResultUseCase.Result.APPLIED);
        assertThat(pendingOrder.status()).isEqualTo(OrderStatus.PAID);
        assertThat(pista.reservedQuantity()).isZero();
        assertThat(pista.soldQuantity()).isEqualTo(2);
        verify(orders).update(pendingOrder);
    }

    @Test
    @DisplayName("refusal turns the order REJECTED and puts the tickets back on sale")
    void refusalReleasesReservation() {
        givenOrderAndCatalogExist();
        int availableBefore = pista.availableQuantity();

        ApplyPaymentResultUseCase.Result result = applyPaymentResult.execute(rejected());

        assertThat(result).isEqualTo(ApplyPaymentResultUseCase.Result.APPLIED);
        assertThat(pendingOrder.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(pista.reservedQuantity()).isZero();
        assertThat(pista.soldQuantity()).isZero();
        // A declined card must not keep tickets off the market.
        assertThat(pista.availableQuantity()).isEqualTo(availableBefore + 2);
        verify(orders).update(pendingOrder);
    }

    @Test
    @DisplayName("records the reason the payment was refused")
    void keepsRefusalReason() {
        givenOrderAndCatalogExist();

        applyPaymentResult.execute(rejected());

        assertThat(pendingOrder.statusHistory()).last().satisfies(change -> {
            assertThat(change.toStatus()).isEqualTo(OrderStatus.REJECTED);
            assertThat(change.reason()).isEqualTo("Card declined by issuer");
        });
    }

    @Test
    @DisplayName("writes the inbox record in the same unit of work")
    void recordsProcessedEvent() {
        givenOrderAndCatalogExist();
        ApplyPaymentResultUseCase.Command command = approved();

        applyPaymentResult.execute(command);

        verify(processedEvents).record(command.eventId());
    }

    @Test
    @DisplayName("a redelivered event changes nothing at all")
    void ignoresDuplicateEvent() {
        // Kafka is at-least-once, so this is a certainty, not an edge case.
        ApplyPaymentResultUseCase.Command command = approved();
        when(processedEvents.alreadyProcessed(command.eventId())).thenReturn(true);

        ApplyPaymentResultUseCase.Result result = applyPaymentResult.execute(command);

        assertThat(result).isEqualTo(ApplyPaymentResultUseCase.Result.IGNORED_DUPLICATE);
        assertThat(pendingOrder.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(pista.reservedQuantity()).isEqualTo(2);
        verifyNoInteractions(orders);
        verify(catalog, never()).updateInventory(anyList());
        verify(processedEvents, never()).record(any());
    }

    @Test
    @DisplayName("an event for an unknown order fails, so the message can go to the DLQ")
    void unknownOrder() {
        when(orders.findById(pendingOrder.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applyPaymentResult.execute(approved()))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orders, never()).update(any());
        verify(processedEvents, never()).record(any());
    }

    @Test
    @DisplayName("approval for a cancelled order is absorbed and flagged for refund")
    void approvalAfterCancellation() {
        // A corrida que o cancelamento assíncrono cria: a pessoa desistiu enquanto o
        // gateway já estava cobrando. Sem este ramo a mensagem estoura, é reentregue
        // três vezes e morre na DLQ - com o cartão cobrado e ninguém sabendo.
        pendingOrder.cancel("Cancelled by the customer", NOW.plusSeconds(1));
        when(orders.findById(pendingOrder.id())).thenReturn(Optional.of(pendingOrder));
        ApplyPaymentResultUseCase.Command command = approved();

        ApplyPaymentResultUseCase.Result result = applyPaymentResult.execute(command);

        assertThat(result).isEqualTo(ApplyPaymentResultUseCase.Result.PAID_AFTER_CLOSE);
        assertThat(pendingOrder.status()).isEqualTo(OrderStatus.CANCELLED);
        // O estoque já voltou no cancelamento; confirmar a venda agora venderia
        // entradas que outra pessoa pode ter comprado nesse meio tempo.
        verify(catalog, never()).updateInventory(anyList());
        verify(orders, never()).update(any());
        // A mensagem foi consumida de verdade: reentregá-la não muda nada.
        verify(processedEvents).record(command.eventId());
    }

    @Test
    @DisplayName("a refusal for a cancelled order is simply consumed")
    void refusalAfterCancellation() {
        // Recusa mais cancelamento não tem compensação: ninguém pagou nada.
        pendingOrder.cancel("Cancelled by the customer", NOW.plusSeconds(1));
        when(orders.findById(pendingOrder.id())).thenReturn(Optional.of(pendingOrder));
        ApplyPaymentResultUseCase.Command command = rejected();

        ApplyPaymentResultUseCase.Result result = applyPaymentResult.execute(command);

        assertThat(result).isEqualTo(ApplyPaymentResultUseCase.Result.IGNORED_CLOSED);
        assertThat(pendingOrder.status()).isEqualTo(OrderStatus.CANCELLED);
        // Quem cancelou já devolveu a reserva ao catálogo. Este caso de uso não pode
        // encostar no estoque de novo: liberar duas vezes a mesma reserva colocaria
        // à venda entradas que não existem.
        verify(catalog, never()).updateInventory(anyList());
        verify(catalog, never()).findById(any());
        verify(processedEvents).record(command.eventId());
    }

    @Test
    @DisplayName("an approval that arrives after the order expired is flagged for refund too")
    void approvalAfterExpiry() {
        pendingOrder.expire(NOW.plusSeconds(1));
        when(orders.findById(pendingOrder.id())).thenReturn(Optional.of(pendingOrder));

        ApplyPaymentResultUseCase.Result result = applyPaymentResult.execute(approved());

        assertThat(result).isEqualTo(ApplyPaymentResultUseCase.Result.PAID_AFTER_CLOSE);
        assertThat(pendingOrder.status()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("a second, different approval for an already paid order is refused")
    void alreadyPaidOrder() {
        // Not the same as a redelivery: a different eventId saying "paid" again means
        // something upstream is wrong, and it must not silently double-count the sale.
        pendingOrder.markPaid(UUID.randomUUID(), NOW.plusSeconds(1));
        when(orders.findById(pendingOrder.id())).thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> applyPaymentResult.execute(approved()))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orders, never()).update(any());
        verify(catalog, never()).updateInventory(anyList());
        verify(processedEvents, never()).record(any());
    }
}
