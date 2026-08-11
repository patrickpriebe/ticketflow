package com.ticketflow.payment.application.strategy;

import com.ticketflow.payment.application.port.out.PaymentGateway.AuthorizationRequest;
import com.ticketflow.payment.domain.model.Money;
import com.ticketflow.payment.domain.model.Payer;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStrategiesTest {

    private static final Instant NOW = Instant.parse("2026-08-10T14:00:00Z");

    private static final List<PaymentStrategy> ALL = List.of(
            new CreditCardPaymentStrategy(),
            new PixPaymentStrategy(),
            new BoletoPaymentStrategy());

    private final PaymentStrategies strategies = new PaymentStrategies(ALL);

    private Payment payment(PaymentMethod method) {
        return Payment.forOrder(UUID.randomUUID(), UUID.randomUUID(),
                Money.of("1300.00", "BRL"), method, NOW);
    }

    private Payer payer() {
        return new Payer(UUID.randomUUID(), "Ana Souza", "ana.souza@example.com");
    }

    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    @DisplayName("every payment method resolves to its own strategy")
    void everyMethodIsCovered(PaymentMethod method) {
        assertThat(strategies.forMethod(method).method()).isEqualTo(method);
    }

    @Test
    @DisplayName("refuses to start when a method has no strategy")
    void failsFastOnMissingStrategy() {
        // The point of the registry: adding a PaymentMethod without its strategy
        // breaks start-up loudly instead of failing on a real customer's order.
        assertThatThrownBy(() -> new PaymentStrategies(List.of(new PixPaymentStrategy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREDIT_CARD")
                .hasMessageContaining("BOLETO");
    }

    @Test
    @DisplayName("refuses two strategies claiming the same method")
    void failsFastOnDuplicateStrategy() {
        assertThatThrownBy(() -> new PaymentStrategies(List.of(
                new CreditCardPaymentStrategy(), new CreditCardPaymentStrategy(),
                new PixPaymentStrategy(), new BoletoPaymentStrategy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two strategies claim CREDIT_CARD");
    }

    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    @DisplayName("every request is idempotency-keyed by the payment id")
    void requestsAreIdempotencyKeyed(PaymentMethod method) {
        Payment payment = payment(method);

        AuthorizationRequest request = strategies.forMethod(method).buildRequest(payment, payer());

        // Stable across retries of the same payment, which is what makes retrying a
        // timed-out call safe.
        assertThat(request.idempotencyKey()).isEqualTo(payment.id().toString());
        assertThat(request.body()).containsEntry("reference", payment.orderId().toString());
    }

    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    @DisplayName("no request ever carries card credentials")
    void noCardDataLeaves(PaymentMethod method) {
        AuthorizationRequest request = strategies.forMethod(method)
                .buildRequest(payment(method), payer());

        assertThat(request.body().keySet())
                .doesNotContain("cardNumber", "pan", "cvv", "securityCode", "expiry");
    }

    @Test
    @DisplayName("each method uses its own endpoint")
    void endpointsDiffer() {
        assertThat(strategies.forMethod(PaymentMethod.CREDIT_CARD)
                .buildRequest(payment(PaymentMethod.CREDIT_CARD), payer()).path())
                .isEqualTo("/v1/charges");
        assertThat(strategies.forMethod(PaymentMethod.PIX)
                .buildRequest(payment(PaymentMethod.PIX), payer()).path())
                .isEqualTo("/v1/pix/charges");
        assertThat(strategies.forMethod(PaymentMethod.BOLETO)
                .buildRequest(payment(PaymentMethod.BOLETO), payer()).path())
                .isEqualTo("/v1/boletos");
    }

    @Test
    @DisplayName("a boleto is not retried on timeout, a card charge is")
    void retryPolicyDiffersByMethod() {
        assertThat(strategies.forMethod(PaymentMethod.CREDIT_CARD).retryOnTimeout()).isTrue();
        assertThat(strategies.forMethod(PaymentMethod.PIX).retryOnTimeout()).isTrue();
        // Retrying would hand the customer a second slip for the same order.
        assertThat(strategies.forMethod(PaymentMethod.BOLETO).retryOnTimeout()).isFalse();
    }

    @Test
    @DisplayName("the credit card request carries the amount and the payer, nothing more")
    void creditCardBody() {
        Payment payment = payment(PaymentMethod.CREDIT_CARD);
        Payer payer = payer();

        Map<String, Object> body = strategies.forMethod(PaymentMethod.CREDIT_CARD)
                .buildRequest(payment, payer).body();

        assertThat(body).containsEntry("method", "credit_card");
        assertThat(body).containsEntry("currency", "BRL");
        assertThat(body.get("amount")).hasToString("1300.00");
    }
}
