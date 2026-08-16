package com.ticketflow.payment.application.usecase;

import com.ticketflow.payment.application.port.in.RefundCancelledOrderUseCase;
import com.ticketflow.payment.application.port.in.RefundCancelledOrderUseCase.Command;
import com.ticketflow.payment.application.port.in.RefundCancelledOrderUseCase.Result;
import com.ticketflow.payment.application.port.out.PaymentGateway;
import com.ticketflow.payment.application.port.out.PaymentGateway.RefundRequest;
import com.ticketflow.payment.application.port.out.PaymentGateway.RefundResponse;
import com.ticketflow.payment.application.port.out.PaymentRepository;
import com.ticketflow.payment.application.port.out.ProcessedEventRepository;
import com.ticketflow.payment.application.port.out.UnitOfWork;
import com.ticketflow.payment.domain.exception.PaymentGatewayUnavailableException;
import com.ticketflow.payment.domain.model.Money;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentAttempt;
import com.ticketflow.payment.domain.model.PaymentMethod;
import com.ticketflow.payment.domain.model.PaymentStatus;
import com.ticketflow.payment.domain.model.AttemptOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundCancelledOrderTest {

    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

    @Mock
    private PaymentRepository payments;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private ProcessedEventRepository processedEvents;

    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }

        @Override
        public void executeVoid(Runnable work) {
            work.run();
        }
    };

    private RefundCancelledOrder refundCancelledOrder;
    private UUID orderId;
    private UUID customerId;
    private Command command;

    @BeforeEach
    void setUp() {
        refundCancelledOrder = new RefundCancelledOrder(
                payments, gateway, processedEvents, unitOfWork, Clock.fixed(NOW, ZoneOffset.UTC));
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        command = new Command(UUID.randomUUID(), orderId, customerId,
                Money.of("335.00", "BRL"), PaymentMethod.CREDIT_CARD,
                "Cancelled by the customer", NOW);
    }

    private Payment pendingPayment() {
        return Payment.forOrder(orderId, customerId, Money.of("335.00", "BRL"),
                PaymentMethod.CREDIT_CARD, NOW.minusSeconds(60));
    }

    private Payment approvedPayment() {
        Payment payment = pendingPayment();
        payment.applyGatewayOutcome(
                new PaymentAttempt(1, AttemptOutcome.APPROVED, 200, 120, null, null, null),
                "acme-payments", "ch_123", null, null, NOW.minusSeconds(30));
        return payment;
    }

    @Test
    @DisplayName("cobranca aprovada e estornada, e o comprovante fica guardado")
    void refundsAnApprovedCharge() {
        Payment approved = approvedPayment();
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(approved));
        when(gateway.refund(any())).thenReturn(RefundResponse.refunded("re_999", 200));

        Result result = refundCancelledOrder.execute(command);

        assertThat(result).isEqualTo(Result.REFUNDED);
        assertThat(approved.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(approved.refundId()).isEqualTo("re_999");
        // A transação original continua junto: é ela que liga o estorno ao que saiu.
        assertThat(approved.gatewayTransactionId()).isEqualTo("ch_123");
        verify(payments).update(approved);
        verify(processedEvents).record(command.eventId());
    }

    @Test
    @DisplayName("o estorno usa o id da cobranca como chave, para nao devolver duas vezes")
    void refundIsIdempotentAtTheProvider() {
        Payment approved = approvedPayment();
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(approved));
        when(gateway.refund(any())).thenReturn(RefundResponse.refunded("re_999", 200));

        refundCancelledOrder.execute(command);

        ArgumentCaptor<RefundRequest> sent = ArgumentCaptor.forClass(RefundRequest.class);
        verify(gateway).refund(sent.capture());
        assertThat(sent.getValue().idempotencyKey()).isEqualTo(approved.id().toString());
        assertThat(sent.getValue().transactionId()).isEqualTo("ch_123");
        assertThat(sent.getValue().amount()).isEqualByComparingTo("335.00");
    }

    @Test
    @DisplayName("cobranca ainda nao feita e fechada sem falar com o provedor")
    void cancelsBeforeCharge() {
        Payment pending = pendingPayment();
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(pending));

        Result result = refundCancelledOrder.execute(command);

        assertThat(result).isEqualTo(Result.CANCELLED_BEFORE_CHARGE);
        assertThat(pending.status()).isEqualTo(PaymentStatus.CANCELLED);
        // Não há o que desfazer: chamar o provedor aqui seria pedir estorno de
        // uma cobrança que nunca existiu.
        verifyNoInteractions(gateway);
        verify(payments).update(pending);
        verify(processedEvents).record(command.eventId());
    }

    @Test
    @DisplayName("cancelamento que chega antes do ORDER_CREATED registra a cobranca ja cancelada")
    void blocksAChargeThatDoesNotExistYet() {
        // O cruzamento mais perigoso. Sem esta linha, o ORDER_CREATED chegaria
        // depois, criaria a cobrança e cobraria o cartão de quem já desistiu — e
        // ninguém estornaria, porque este evento já teria sido consumido.
        when(payments.findByOrderId(orderId)).thenReturn(Optional.empty());

        Result result = refundCancelledOrder.execute(command);

        assertThat(result).isEqualTo(Result.BLOCKED_BEFORE_CREATION);
        verifyNoInteractions(gateway);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(saved.getValue().orderId()).isEqualTo(orderId);
        // isSettled() é o que o ProcessOrderPayment consulta antes de cobrar. Se
        // isto for falso, a proteção inteira não existe.
        assertThat(saved.getValue().isSettled()).isTrue();
        verify(processedEvents).record(command.eventId());
    }

    @Test
    @DisplayName("provedor sem resposta nao marca nada, para a mensagem voltar")
    void unavailableProviderIsRetried() {
        Payment approved = approvedPayment();
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(approved));
        when(gateway.refund(any())).thenReturn(RefundResponse.unavailable("timeout", null));

        assertThatThrownBy(() -> refundCancelledOrder.execute(command))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        // Nada gravado: se o inbox fosse marcado aqui, a reentrega seria
        // descartada e o dinheiro nunca voltaria.
        assertThat(approved.status()).isEqualTo(PaymentStatus.APPROVED);
        verify(payments, never()).update(any());
        verify(processedEvents, never()).record(any());
    }

    @Test
    @DisplayName("recusa definitiva do provedor para de tentar e deixa o caso visivel")
    void declinedRefundStopsRetrying() {
        Payment approved = approvedPayment();
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(approved));
        when(gateway.refund(any())).thenReturn(RefundResponse.declined("já estornado fora do sistema", 400));

        Result result = refundCancelledOrder.execute(command);

        assertThat(result).isEqualTo(Result.NOTHING_TO_REFUND);
        // Continua APPROVED de propósito: uma cobrança aprovada de pedido
        // cancelado é exatamente o que alguém precisa enxergar.
        assertThat(approved.status()).isEqualTo(PaymentStatus.APPROVED);
        verify(processedEvents).record(command.eventId());
    }

    @Test
    @DisplayName("cobranca recusada nao tem o que estornar")
    void rejectedPaymentHasNothingToRefund() {
        Payment rejected = pendingPayment();
        rejected.applyGatewayOutcome(
                new PaymentAttempt(1, AttemptOutcome.REJECTED, 402, 90, null, null, null),
                "acme-payments", null, "INSUFFICIENT_FUNDS", "Sem saldo", NOW.minusSeconds(30));
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(rejected));

        Result result = refundCancelledOrder.execute(command);

        assertThat(result).isEqualTo(Result.NOTHING_TO_REFUND);
        assertThat(rejected.status()).isEqualTo(PaymentStatus.REJECTED);
        verifyNoInteractions(gateway);
        verify(processedEvents).record(command.eventId());
    }

    @Test
    @DisplayName("mensagem reentregue nao estorna de novo")
    void duplicateEventDoesNothing() {
        when(processedEvents.alreadyProcessed(command.eventId())).thenReturn(true);

        Result result = refundCancelledOrder.execute(command);

        assertThat(result).isEqualTo(RefundCancelledOrderUseCase.Result.IGNORED_DUPLICATE);
        verifyNoInteractions(gateway);
        verifyNoInteractions(payments);
    }
}
