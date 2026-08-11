package com.ticketflow.payment.application.strategy;

import com.ticketflow.payment.application.port.out.PaymentGateway.AuthorizationRequest;
import com.ticketflow.payment.domain.model.Payer;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentMethod;

import java.util.LinkedHashMap;
import java.util.Map;

public class BoletoPaymentStrategy implements PaymentStrategy {

    private static final int DUE_IN_DAYS = 3;

    @Override
    public PaymentMethod method() {
        return PaymentMethod.BOLETO;
    }

    @Override
    public AuthorizationRequest buildRequest(Payment payment, Payer payer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", payment.amount().amount());
        body.put("currency", payment.amount().currency());
        body.put("method", "boleto");
        body.put("dueInDays", DUE_IN_DAYS);
        body.put("reference", payment.orderId().toString());
        body.put("payer", Map.of(
                "id", payer.customerId().toString(),
                "name", payer.name() == null ? "" : payer.name(),
                "email", payer.email()));

        return new AuthorizationRequest(payment.id(), payment.id().toString(), "/v1/boletos", body);
    }

    @Override
    public boolean retryOnTimeout() {
        // Unlike a card charge, a timed-out boleto is not retried: if the provider
        // did create the slip, a second call would hand the customer two boletos for
        // the same order and one of them would go unpaid and unexplained.
        return false;
    }
}
