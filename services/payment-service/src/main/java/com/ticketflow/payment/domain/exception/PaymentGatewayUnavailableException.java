package com.ticketflow.payment.domain.exception;

import java.util.UUID;

/**
 * The gateway gave no usable answer and the call is worth retrying.
 *
 * <p>Thrown <em>after</em> the failed attempt has been committed, so the audit trail
 * survives. It propagates out of the consumer on purpose: letting the Kafka message
 * be redelivered is the retry mechanism, and the DLQ is where it lands if the
 * gateway never recovers.
 */
public class PaymentGatewayUnavailableException extends DomainException {

    public PaymentGatewayUnavailableException(UUID orderId, String detail) {
        super("payment-gateway-unavailable",
                "Gateway gave no usable answer for order %s: %s".formatted(orderId, detail));
    }
}
