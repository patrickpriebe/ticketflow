package com.ticketflow.payment.application.strategy;

import com.ticketflow.payment.application.port.out.PaymentGateway.AuthorizationRequest;
import com.ticketflow.payment.domain.model.Payer;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentMethod;

import java.util.LinkedHashMap;
import java.util.Map;

public class CreditCardPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod method() {
        return PaymentMethod.CREDIT_CARD;
    }

    @Override
    public AuthorizationRequest buildRequest(Payment payment, Payer payer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", payment.amount().amount());
        body.put("currency", payment.amount().currency());
        body.put("method", "credit_card");
        body.put("capture", true);
        body.put("installments", 1);
        body.put("reference", payment.orderId().toString());
        body.put("customer", Map.of(
                "id", payer.customerId().toString(),
                "email", payer.email()));
        // No card number, no CVV, no expiry: the customer's credentials are
        // tokenised by the provider's own checkout and never reach this service.
        return new AuthorizationRequest(payment.id(), payment.id().toString(), "/v1/charges", body);
    }

    @Override
    public boolean retryOnTimeout() {
        // Safe because the request carries an idempotency key: if the first call did
        // reach the provider, the retry returns that same charge rather than a second one.
        return true;
    }
}
