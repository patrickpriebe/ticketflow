package com.ticketflow.payment.application.strategy;

import com.ticketflow.payment.application.port.out.PaymentGateway.AuthorizationRequest;
import com.ticketflow.payment.domain.model.Payer;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentMethod;

import java.util.LinkedHashMap;
import java.util.Map;

public class PixPaymentStrategy implements PaymentStrategy {

    /** A PIX charge is short-lived; the provider expires it if unpaid. */
    private static final int EXPIRES_IN_SECONDS = 900;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.PIX;
    }

    @Override
    public AuthorizationRequest buildRequest(Payment payment, Payer payer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", payment.amount().amount());
        body.put("currency", payment.amount().currency());
        body.put("method", "pix");
        body.put("expiresIn", EXPIRES_IN_SECONDS);
        body.put("reference", payment.orderId().toString());
        body.put("payer", Map.of(
                "id", payer.customerId().toString(),
                "name", payer.name() == null ? "" : payer.name(),
                "email", payer.email()));

        // Different endpoint from a card charge - which is exactly why this is a
        // strategy and not a boolean flag.
        return new AuthorizationRequest(payment.id(), payment.id().toString(), "/v1/pix/charges", body);
    }

    @Override
    public boolean retryOnTimeout() {
        return true;
    }
}
