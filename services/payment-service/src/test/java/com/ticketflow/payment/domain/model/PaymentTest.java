package com.ticketflow.payment.domain.model;

import com.ticketflow.payment.domain.exception.InvalidPaymentStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-08-10T14:00:00Z");
    private static final String GATEWAY = "acme-payments";

    private Payment pendingPayment() {
        return Payment.forOrder(UUID.randomUUID(), UUID.randomUUID(),
                Money.of("1300.00", "BRL"), PaymentMethod.CREDIT_CARD, NOW);
    }

    private PaymentAttempt attempt(AttemptOutcome outcome, int number) {
        return new PaymentAttempt(number, outcome, 200, 120, "{}", "{}", null);
    }

    @Test
    @DisplayName("starts PENDING with no attempts")
    void startsPending() {
        Payment payment = pendingPayment();

        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.attempts()).isZero();
        assertThat(payment.isSettled()).isFalse();
    }

    @Nested
    @DisplayName("gateway outcomes")
    class Outcomes {

        @Test
        @DisplayName("approval stores the transaction id and the authorisation time")
        void approval() {
            Payment payment = pendingPayment();

            payment.applyGatewayOutcome(attempt(AttemptOutcome.APPROVED, 1),
                    GATEWAY, "ch_123", null, null, NOW.plusSeconds(2));

            assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.gatewayTransactionId()).isEqualTo("ch_123");
            assertThat(payment.authorizedAt()).isEqualTo(NOW.plusSeconds(2));
            assertThat(payment.attempts()).isEqualTo(1);
            assertThat(payment.failureCode()).isNull();
        }

        @Test
        @DisplayName("an approval without a transaction id is refused outright")
        void approvalNeedsTransactionId() {
            Payment payment = pendingPayment();

            assertThatThrownBy(() -> payment.applyGatewayOutcome(
                    attempt(AttemptOutcome.APPROVED, 1), GATEWAY, null, null, null, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("transaction id");
        }

        @Test
        @DisplayName("a decline is REJECTED and keeps the reason")
        void decline() {
            Payment payment = pendingPayment();

            payment.applyGatewayOutcome(attempt(AttemptOutcome.REJECTED, 1),
                    GATEWAY, null, "INSUFFICIENT_FUNDS", "Card declined by issuer", NOW.plusSeconds(2));

            assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);
            assertThat(payment.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
            assertThat(payment.isSettled()).isTrue();
        }

        @Test
        @DisplayName("a timeout is FAILED, not REJECTED - nobody knows if the money moved")
        void timeoutIsNotDecline() {
            Payment payment = pendingPayment();

            payment.applyGatewayOutcome(
                    new PaymentAttempt(1, AttemptOutcome.TIMEOUT, null, 5000, "{}", null, "read timed out"),
                    GATEWAY, null, "GATEWAY_TIMEOUT", "No answer in time", NOW.plusSeconds(5));

            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
            // Not settled: a FAILED payment is unresolved and may still be retried.
            assertThat(payment.isSettled()).isFalse();
        }

        @Test
        @DisplayName("a 5xx is FAILED too")
        void serverErrorIsFailed() {
            Payment payment = pendingPayment();

            payment.applyGatewayOutcome(
                    new PaymentAttempt(1, AttemptOutcome.ERROR, 503, 80, "{}", "{}", "service unavailable"),
                    GATEWAY, null, "GATEWAY_ERROR", "HTTP 503", NOW.plusSeconds(1));

            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("retrying")
    class Retrying {

        @Test
        @DisplayName("a FAILED payment can still be approved on a later attempt")
        void failedCanBeRetried() {
            Payment payment = pendingPayment();
            payment.applyGatewayOutcome(
                    new PaymentAttempt(1, AttemptOutcome.TIMEOUT, null, 5000, "{}", null, "timeout"),
                    GATEWAY, null, "GATEWAY_TIMEOUT", "No answer", NOW.plusSeconds(5));

            payment.applyGatewayOutcome(attempt(AttemptOutcome.APPROVED, 2),
                    GATEWAY, "ch_456", null, null, NOW.plusSeconds(10));

            assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.attempts()).isEqualTo(2);
            assertThat(payment.newAttempts()).hasSize(2);
        }

        @Test
        @DisplayName("an approved payment is never charged again")
        void approvedIsTerminal() {
            Payment payment = pendingPayment();
            payment.applyGatewayOutcome(attempt(AttemptOutcome.APPROVED, 1),
                    GATEWAY, "ch_123", null, null, NOW.plusSeconds(2));

            assertThatThrownBy(() -> payment.applyGatewayOutcome(
                    attempt(AttemptOutcome.APPROVED, 2), GATEWAY, "ch_999", null, null, NOW.plusSeconds(9)))
                    .isInstanceOf(InvalidPaymentStatusTransitionException.class);

            assertThat(payment.gatewayTransactionId()).isEqualTo("ch_123");
        }

        @Test
        @DisplayName("a rejected payment is not silently re-approved")
        void rejectedIsTerminal() {
            Payment payment = pendingPayment();
            payment.applyGatewayOutcome(attempt(AttemptOutcome.REJECTED, 1),
                    GATEWAY, null, "INSUFFICIENT_FUNDS", "declined", NOW.plusSeconds(2));

            assertThatThrownBy(() -> payment.applyGatewayOutcome(
                    attempt(AttemptOutcome.APPROVED, 2), GATEWAY, "ch_123", null, null, NOW.plusSeconds(9)))
                    .isInstanceOf(InvalidPaymentStatusTransitionException.class);
        }
    }

    @Test
    @DisplayName("only timeouts and errors are worth retrying")
    void retryability() {
        assertThat(AttemptOutcome.TIMEOUT.isRetryable()).isTrue();
        assertThat(AttemptOutcome.ERROR.isRetryable()).isTrue();
        // Retrying a decline just annoys the issuer; the answer will be the same.
        assertThat(AttemptOutcome.REJECTED.isRetryable()).isFalse();
        assertThat(AttemptOutcome.APPROVED.isRetryable()).isFalse();
    }
}
