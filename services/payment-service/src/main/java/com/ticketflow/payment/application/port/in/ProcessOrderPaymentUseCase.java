package com.ticketflow.payment.application.port.in;

import com.ticketflow.payment.domain.model.Money;
import com.ticketflow.payment.domain.model.Payer;
import com.ticketflow.payment.domain.model.PaymentMethod;

import java.util.Objects;
import java.util.UUID;

/** Driving port: charge the customer for an order that was just placed. */
public interface ProcessOrderPaymentUseCase {

    Result execute(Command command);

    record Command(UUID eventId,
                   UUID orderId,
                   Payer payer,
                   Money amount,
                   PaymentMethod method) {

        public Command {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(orderId, "orderId is required");
            Objects.requireNonNull(payer, "payer is required");
            Objects.requireNonNull(amount, "amount is required");
            Objects.requireNonNull(method, "method is required");
        }
    }

    enum Result {
        APPROVED,
        REJECTED,
        /** This ORDER_CREATED had already been handled. */
        IGNORED_DUPLICATE,
        /** A payment for this order already reached a final answer. */
        ALREADY_SETTLED,
        /**
         * The provider took the charge and will answer by webhook. Nothing was
         * published: announcing an outcome that does not exist yet is how a ticket
         * gets issued for a boleto nobody paid.
         */
        AWAITING_PROVIDER,
        /**
         * The gateway gave no usable answer and this method must not be retried
         * automatically. Left FAILED for an operator; the order expires on its own.
         */
        ABANDONED
    }
}
