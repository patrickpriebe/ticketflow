package com.ticketflow.payment.application.usecase;

import com.ticketflow.payment.application.port.in.FindOrderPaymentUseCase.View;
import com.ticketflow.payment.application.port.out.PaymentIntentReader;
import com.ticketflow.payment.application.port.out.PaymentRepository;
import com.ticketflow.payment.domain.model.AttemptOutcome;
import com.ticketflow.payment.domain.model.Money;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentAttempt;
import com.ticketflow.payment.domain.model.PaymentMethod;
import com.ticketflow.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O que decide se um segredo de cobrança sai daqui.
 */
class FindOrderPaymentTest {

    private static final UUID ORDER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OWNER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SOMEONE_ELSE = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String INTENT = "pi_teste_123";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"), ZoneOffset.UTC);

    private PaymentRepository payments;
    private PaymentIntentReader intents;
    private FindOrderPayment useCase;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        intents = mock(PaymentIntentReader.class);
        useCase = new FindOrderPayment(payments, intents);
    }

    private static PaymentAttempt attempt(int number, AttemptOutcome outcome) {
        return new PaymentAttempt(number, outcome, 200, 30, "{}", "{}", null);
    }

    private static Payment awaitingCard() {
        Payment payment = Payment.forOrder(ORDER, OWNER,
                new Money(new BigDecimal("120.00"), "BRL"), PaymentMethod.CREDIT_CARD, CLOCK.instant());
        payment.awaitProviderConfirmation(
                attempt(1, AttemptOutcome.ACCEPTED), "stripe", INTENT, CLOCK.instant());
        return payment;
    }

    @Test
    @DisplayName("o dono recebe o segredo enquanto a cobranca espera confirmacao")
    void ownerGetsTheSecret() {
        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(awaitingCard()));
        when(intents.clientSecretFor(INTENT)).thenReturn(Optional.of("pi_teste_123_secret_abc"));

        Optional<View> view = useCase.execute(ORDER, OWNER);

        assertThat(view).isPresent();
        assertThat(view.get().clientSecret()).isEqualTo("pi_teste_123_secret_abc");
    }

    @Test
    @DisplayName("o valor da cobranca sai junto, inclusive depois de estornada")
    void ownerGetsTheAmount() {
        // É o que permite a tela do pedido cancelado dizer quanto voltou. Sem o
        // valor, ela só consegue dizer "estornado" — e aí a pessoa vai conferir a
        // fatura para saber o que voltou, que é o trabalho que a tela existe para
        // poupar.
        Payment refunded = awaitingCard();
        refunded.applyGatewayOutcome(attempt(2, AttemptOutcome.APPROVED),
                "stripe", INTENT, null, null, CLOCK.instant());
        refunded.markRefunded("re_teste_999", CLOCK.instant());

        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(refunded));

        Optional<View> view = useCase.execute(ORDER, OWNER);

        assertThat(view).isPresent();
        assertThat(view.get().amount().amount()).isEqualByComparingTo("120.00");
        assertThat(view.get().amount().currency()).isEqualTo("BRL");
        assertThat(view.get().status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("cobranca estornada durante a corrida aparece como estornada, apesar do status CANCELLED")
    void refundedDuringRaceIsReportedAsRefunded() {
        // O caso que o status sozinho nao conta. Quando o cancelamento cruza uma
        // cobranca em voo, o dinheiro volta mas o status fica CANCELLED — porque
        // foi isso que aconteceu com o PEDIDO. Perguntar so pelo status faria a
        // tela dizer "nenhuma cobranca foi feita" para alguem que foi cobrado.
        Payment payment = Payment.forOrder(ORDER, OWNER,
                new Money(new BigDecimal("120.00"), "BRL"), PaymentMethod.CREDIT_CARD, CLOCK.instant());
        payment.cancelBeforeCharge("Cancelled by the customer", CLOCK.instant());
        payment.recordRefundOfCancelledOrder("ch_123", "re_da_corrida", "stripe", CLOCK.instant());

        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(payment));

        Optional<View> view = useCase.execute(ORDER, OWNER);

        assertThat(view).isPresent();
        assertThat(view.get().status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(view.get().refunded())
                .as("o comprovante do estorno e o que decide, nao o rotulo do status")
                .isTrue();
    }

    @Test
    @DisplayName("cobranca cancelada sem estorno nao se diz estornada")
    void cancelledWithoutRefundIsNotRefunded() {
        Payment payment = Payment.forOrder(ORDER, OWNER,
                new Money(new BigDecimal("120.00"), "BRL"), PaymentMethod.CREDIT_CARD, CLOCK.instant());
        payment.cancelBeforeCharge("Cancelled by the customer", CLOCK.instant());

        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(payment));

        assertThat(useCase.execute(ORDER, OWNER)).get()
                .extracting(View::refunded)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("o valor tambem nao vaza para quem nao e dono")
    void foreignRequesterGetsNoAmount() {
        // O campo novo entra pelo mesmo caminho do segredo, então herda o filtro
        // de dono. Este teste trava isso: adicionar dado ao View não pode virar
        // uma porta que escapa da regra que já existia.
        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(awaitingCard()));

        assertThat(useCase.execute(ORDER, SOMEONE_ELSE)).isEmpty();
    }

    @Test
    @DisplayName("pedido de outra pessoa responde igual a pedido inexistente")
    void foreignOrderLooksMissing() {
        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(awaitingCard()));

        assertThat(useCase.execute(ORDER, SOMEONE_ELSE))
                .as("""
                        Distinguir "não é seu" de "não existe" entregaria, para quem
                        sonda ids, a informação de que aquele pedido existe. O Order
                        Service faz a mesma escolha em GetOrder.""")
                .isEmpty();
    }

    @Test
    @DisplayName("nao consulta o provedor quando o pedido nao e de quem pediu")
    void doesNotTouchTheProviderForForeignOrders() {
        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(awaitingCard()));

        useCase.execute(ORDER, SOMEONE_ELSE);

        verify(intents, never()).clientSecretFor(any());
    }

    @Test
    @DisplayName("pedido sem cobranca nenhuma nao existe para esta consulta")
    void missingPayment() {
        when(payments.findByOrderId(ORDER)).thenReturn(Optional.empty());

        assertThat(useCase.execute(ORDER, OWNER)).isEmpty();
    }

    @Test
    @DisplayName("cobranca ja resolvida nao devolve segredo")
    void settledPaymentHasNoSecret() {
        Payment payment = awaitingCard();
        payment.applyGatewayOutcome(attempt(2, AttemptOutcome.APPROVED),
                "stripe", INTENT, null, null, CLOCK.instant());

        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(payment));

        Optional<View> view = useCase.execute(ORDER, OWNER);

        assertThat(view).isPresent();
        assertThat(view.get().clientSecret())
                .as("Pago não tem o que confirmar, e um segredo que continua saindo "
                        + "depois de perder a utilidade é superfície exposta de graça.")
                .isNull();
        verify(intents, never()).clientSecretFor(any());
    }
}
