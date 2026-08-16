package com.ticketflow.payment.application.usecase;

import com.ticketflow.payment.application.port.in.ProcessOrderPaymentUseCase;
import com.ticketflow.payment.application.port.out.DomainEventPublisher;
import com.ticketflow.payment.application.port.out.PaymentGateway;
import com.ticketflow.payment.application.port.out.PaymentGateway.AuthorizationResponse;
import com.ticketflow.payment.application.port.out.PaymentRepository;
import com.ticketflow.payment.application.port.out.ProcessedEventRepository;
import com.ticketflow.payment.application.port.out.UnitOfWork;
import com.ticketflow.payment.application.strategy.BoletoPaymentStrategy;
import com.ticketflow.payment.application.strategy.CreditCardPaymentStrategy;
import com.ticketflow.payment.application.strategy.PaymentStrategies;
import com.ticketflow.payment.application.strategy.PixPaymentStrategy;
import com.ticketflow.payment.domain.event.PaymentSettled;
import com.ticketflow.payment.domain.exception.PaymentGatewayUnavailableException;
import com.ticketflow.payment.domain.model.Money;
import com.ticketflow.payment.domain.model.Payer;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentMethod;
import com.ticketflow.payment.domain.model.PaymentStatus;
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
import java.util.List;
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
class ProcessOrderPaymentTest {

    private static final Instant NOW = Instant.parse("2026-08-10T14:00:00Z");
    private static final String GATEWAY = "acme-payments";

    @Mock
    private PaymentRepository payments;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private ProcessedEventRepository processedEvents;

    private final UnitOfWork unitOfWork = new UnitOfWork() {
        @Override
        public <T> T execute(Supplier<T> work) {
            return work.get();
        }
    };

    private ProcessOrderPayment processOrderPayment;
    private UUID orderId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        PaymentStrategies strategies = new PaymentStrategies(List.of(
                new CreditCardPaymentStrategy(), new PixPaymentStrategy(), new BoletoPaymentStrategy()));

        processOrderPayment = new ProcessOrderPayment(payments, gateway, strategies, eventPublisher,
                processedEvents, unitOfWork, Clock.fixed(NOW, ZoneOffset.UTC), GATEWAY);

        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();
    }

    private ProcessOrderPaymentUseCase.Command command(PaymentMethod method) {
        return new ProcessOrderPaymentUseCase.Command(
                eventId, orderId,
                new Payer(UUID.randomUUID(), "Ana Souza", "ana.souza@example.com"),
                Money.of("1300.00", "BRL"), method);
    }

    private void givenNewPayment() {
        when(payments.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("an approved charge settles the payment and announces PAGAMENTO_APROVADO")
    void approved() {
        givenNewPayment();
        when(gateway.authorize(any())).thenReturn(
                AuthorizationResponse.approved("ch_123", 201, 140, "{\"status\":\"approved\"}"));

        ProcessOrderPaymentUseCase.Result result =
                processOrderPayment.execute(command(PaymentMethod.CREDIT_CARD));

        assertThat(result).isEqualTo(ProcessOrderPaymentUseCase.Result.APPROVED);

        ArgumentCaptor<PaymentSettled> published = ArgumentCaptor.forClass(PaymentSettled.class);
        verify(eventPublisher).publish(published.capture());
        assertThat(published.getValue().eventType()).isEqualTo("PAGAMENTO_APROVADO");
        assertThat(published.getValue().payment().gatewayTransactionId()).isEqualTo("ch_123");
        verify(processedEvents).record(eventId);
    }

    @Test
    @DisplayName("cancelamento que passa durante a cobranca faz o dinheiro voltar na hora")
    void cancelledWhileChargeWasInFlight() {
        // A corrida mais cara do cancelamento: o cliente desiste enquanto a
        // cobrança está a caminho do provedor. O objeto em memória ainda diz
        // PENDING, a linha no banco já diz CANCELLED, e o cartão acabou de ser
        // debitado.
        //
        // Sem o estorno aqui o desfecho é silencioso e péssimo: o update esbarra
        // no lock otimista, a mensagem é reentregue, a segunda entrega vê a
        // cobrança já resolvida e devolve ALREADY_SETTLED sem chamar o provedor.
        // Ninguém erra e o cartão fica cobrado de um pedido cancelado.
        Payment inFlight = Payment.forOrder(orderId, UUID.randomUUID(),
                Money.of("1300.00", "BRL"), PaymentMethod.CREDIT_CARD, NOW);
        Payment cancelledMeanwhile = Payment.forOrder(orderId, inFlight.customerId(),
                Money.of("1300.00", "BRL"), PaymentMethod.CREDIT_CARD, NOW);
        cancelledMeanwhile.cancelBeforeCharge("Cancelled by the customer", NOW);

        when(payments.findByOrderId(orderId))
                // 1ª: a cobrança ainda não existe, então é criada
                .thenReturn(Optional.empty())
                // 2ª: a releitura depois do gateway já encontra o cancelamento
                .thenReturn(Optional.of(cancelledMeanwhile))
                // 3ª: dentro da transação que grava o estorno
                .thenReturn(Optional.of(cancelledMeanwhile));
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(gateway.authorize(any())).thenReturn(
                AuthorizationResponse.approved("ch_123", 201, 140, "{}"));
        when(gateway.refund(any())).thenReturn(
                PaymentGateway.RefundResponse.refunded("re_777", 200));

        processOrderPayment.execute(command(PaymentMethod.CREDIT_CARD));

        verify(gateway).refund(any());
        // As duas pontas ficam registradas: saiu e voltou.
        assertThat(cancelledMeanwhile.gatewayTransactionId()).isEqualTo("ch_123");
        assertThat(cancelledMeanwhile.refundId()).isEqualTo("re_777");
        assertThat(cancelledMeanwhile.status()).isEqualTo(PaymentStatus.CANCELLED);
        // Nada de PAGAMENTO_APROVADO: não houve pagamento que valesse.
        verify(eventPublisher, never()).publish(any());
        verify(processedEvents).record(eventId);
    }

    @Test
    @DisplayName("a declined charge announces PAGAMENTO_RECUSADO with the reason")
    void rejected() {
        givenNewPayment();
        when(gateway.authorize(any())).thenReturn(AuthorizationResponse.rejected(
                "INSUFFICIENT_FUNDS", "Card declined by issuer", 402, 130, "{}"));

        ProcessOrderPaymentUseCase.Result result =
                processOrderPayment.execute(command(PaymentMethod.CREDIT_CARD));

        assertThat(result).isEqualTo(ProcessOrderPaymentUseCase.Result.REJECTED);

        ArgumentCaptor<PaymentSettled> published = ArgumentCaptor.forClass(PaymentSettled.class);
        verify(eventPublisher).publish(published.capture());
        assertThat(published.getValue().eventType()).isEqualTo("PAGAMENTO_RECUSADO");
        assertThat(published.getValue().payment().failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("a timeout announces nothing and asks to be retried")
    void timeoutIsRetried() {
        givenNewPayment();
        when(gateway.authorize(any())).thenReturn(
                AuthorizationResponse.timedOut("read timed out after 5000ms", 5000));

        assertThatThrownBy(() -> processOrderPayment.execute(command(PaymentMethod.CREDIT_CARD)))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        // Crucially: no event. Telling the customer their card was declined when the
        // gateway simply did not answer would be a lie.
        verifyNoInteractions(eventPublisher);
        // Not recorded as processed, so the redelivered message tries again.
        verify(processedEvents, never()).record(any());
    }

    @Test
    @DisplayName("the failed attempt is persisted before the retry is requested")
    void timeoutStillRecordsTheAttempt() {
        givenNewPayment();
        when(gateway.authorize(any())).thenReturn(AuthorizationResponse.timedOut("timeout", 5000));

        assertThatThrownBy(() -> processOrderPayment.execute(command(PaymentMethod.CREDIT_CARD)))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        ArgumentCaptor<Payment> updated = ArgumentCaptor.forClass(Payment.class);
        verify(payments).update(updated.capture());
        assertThat(updated.getValue().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updated.getValue().newAttempts()).singleElement()
                .satisfies(a -> assertThat(a.outcome().isRetryable()).isTrue());
    }

    @Test
    @DisplayName("a 5xx asks to be retried too")
    void serverErrorIsRetried() {
        givenNewPayment();
        when(gateway.authorize(any())).thenReturn(
                AuthorizationResponse.errored("HTTP 503", 503, 60, "upstream unavailable"));

        assertThatThrownBy(() -> processOrderPayment.execute(command(PaymentMethod.PIX)))
                .isInstanceOf(PaymentGatewayUnavailableException.class);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("a timed-out boleto is abandoned instead of retried into a second slip")
    void boletoTimeoutIsAbandoned() {
        givenNewPayment();
        when(gateway.authorize(any())).thenReturn(AuthorizationResponse.timedOut("timeout", 5000));

        ProcessOrderPaymentUseCase.Result result =
                processOrderPayment.execute(command(PaymentMethod.BOLETO));

        // The strategy - not the use case - decided this. That is the whole point of
        // having strategies rather than a switch.
        assertThat(result).isEqualTo(ProcessOrderPaymentUseCase.Result.ABANDONED);
        verifyNoInteractions(eventPublisher);
        verify(processedEvents).record(eventId);
    }

    @Test
    @DisplayName("a redelivered ORDER_CREATED never reaches the gateway")
    void duplicateEventIsIgnored() {
        when(processedEvents.alreadyProcessed(eventId)).thenReturn(true);

        ProcessOrderPaymentUseCase.Result result =
                processOrderPayment.execute(command(PaymentMethod.CREDIT_CARD));

        assertThat(result).isEqualTo(ProcessOrderPaymentUseCase.Result.IGNORED_DUPLICATE);
        // The customer must not be charged twice because Kafka delivered twice.
        verifyNoInteractions(gateway);
        verifyNoInteractions(payments);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("an order already paid is not charged again by a different event")
    void alreadySettledOrderIsNotCharged() {
        Payment settled = Payment.forOrder(orderId, UUID.randomUUID(),
                Money.of("1300.00", "BRL"), PaymentMethod.CREDIT_CARD, NOW);
        settled.applyGatewayOutcome(
                new com.ticketflow.payment.domain.model.PaymentAttempt(
                        1, com.ticketflow.payment.domain.model.AttemptOutcome.APPROVED, 201, 100, null, "{}", null),
                GATEWAY, "ch_existing", null, null, NOW);
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(settled));

        ProcessOrderPaymentUseCase.Result result =
                processOrderPayment.execute(command(PaymentMethod.CREDIT_CARD));

        assertThat(result).isEqualTo(ProcessOrderPaymentUseCase.Result.ALREADY_SETTLED);
        verifyNoInteractions(gateway);
        verify(processedEvents).record(eventId);
    }

    @Test
    @DisplayName("a payment left PENDING by an earlier crash is resumed, not duplicated")
    void resumesPendingPayment() {
        Payment pending = Payment.forOrder(orderId, UUID.randomUUID(),
                Money.of("1300.00", "BRL"), PaymentMethod.CREDIT_CARD, NOW);
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(pending));
        when(gateway.authorize(any())).thenReturn(
                AuthorizationResponse.approved("ch_777", 201, 90, "{}"));

        ProcessOrderPaymentUseCase.Result result =
                processOrderPayment.execute(command(PaymentMethod.CREDIT_CARD));

        assertThat(result).isEqualTo(ProcessOrderPaymentUseCase.Result.APPROVED);
        // No second payment row: the existing one was picked up.
        verify(payments, never()).save(any());
    }
}
