package com.ticketflow.payment.domain.exception;

import com.ticketflow.payment.domain.model.PaymentStatus;

public class InvalidPaymentStatusTransitionException extends DomainException {

    public InvalidPaymentStatusTransitionException(PaymentStatus from, PaymentStatus to) {
        super("invalid-payment-transition",
                "A payment cannot go from %s to %s.".formatted(from, to));
    }
}
