package com.ticketflow.payment.domain.event;

import com.ticketflow.payment.domain.model.Payment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The fact that a payment reached a final answer.
 *
 * <p>One domain event covering both outcomes; the messaging adapter turns it into
 * {@code PAGAMENTO_APROVADO} or {@code PAGAMENTO_RECUSADO} on the wire. Those two
 * names are fixed project vocabulary and stay in Portuguese - everything else is
 * English.
 *
 * <p>A payment that merely FAILED never produces this event: "the gateway did not
 * answer" is not the same as "the payment was refused", and telling the customer
 * their card was declined when nobody knows would be a lie.
 */
public record PaymentSettled(UUID eventId, Instant occurredAt, Payment payment) {

    public static final String TYPE_APPROVED = "PAGAMENTO_APROVADO";
    public static final String TYPE_REJECTED = "PAGAMENTO_RECUSADO";

    public PaymentSettled {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(payment, "payment is required");
        if (!payment.status().isFinal()) {
            throw new IllegalArgumentException(
                    "Only a settled payment can be announced, got " + payment.status());
        }
    }

    public static PaymentSettled of(Payment payment, Instant occurredAt) {
        return new PaymentSettled(UUID.randomUUID(), occurredAt, payment);
    }

    public String eventType() {
        return payment.isApproved() ? TYPE_APPROVED : TYPE_REJECTED;
    }
}
