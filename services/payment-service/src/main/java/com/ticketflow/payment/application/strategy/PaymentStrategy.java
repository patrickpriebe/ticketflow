package com.ticketflow.payment.application.strategy;

import com.ticketflow.payment.application.port.out.PaymentGateway.AuthorizationRequest;
import com.ticketflow.payment.domain.model.Payer;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentMethod;

/**
 * How one payment method is charged.
 *
 * <p>This interface is the reason there is no {@code switch (method)} anywhere in
 * the use case. Supporting a new method means writing a new implementation and
 * registering it - no existing file changes behaviour, which is the open/closed
 * principle doing actual work rather than decorating a README.
 *
 * <p>Implementations are plain classes, wired in {@code UseCaseConfiguration}. They
 * carry no Spring annotations, so each one is unit-testable on its own.
 */
public interface PaymentStrategy {

    PaymentMethod method();

    AuthorizationRequest buildRequest(Payment payment, Payer payer);

    /**
     * Whether a timeout should be retried.
     *
     * <p>Not a blanket policy: every request carries an idempotency key, so a card
     * charge can safely be retried, but a boleto cannot - regenerating it would hand
     * the customer a second slip for the same order.
     */
    boolean retryOnTimeout();
}
