package com.ticketflow.notification.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A message owed to the customer.
 *
 * <p>Recorded, not sent: wiring a real SMTP provider adds nothing this project is
 * trying to demonstrate, and would make the tests depend on someone else's uptime.
 * The row is the proof that the decision to notify was taken.
 */
public record Notification(String id,
                           String orderId,
                           String customerId,
                           Channel channel,
                           Type type,
                           String recipient,
                           String subject,
                           String body,
                           Status status,
                           int attempts,
                           Instant createdAt,
                           Instant sentAt) {

    public enum Channel { EMAIL, SMS, PUSH }

    public enum Type { TICKET_ISSUED, PAYMENT_REJECTED, ORDER_CANCELLED }

    public enum Status { PENDING, SENT, FAILED }

    public Notification {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(channel, "channel is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(recipient, "recipient is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    /** Deterministic id, for the same reason tickets have one: redelivery must not duplicate. */
    private static String idFor(String orderId, Type type) {
        return java.util.UUID.nameUUIDFromBytes(
                "%s|%s".formatted(orderId, type).getBytes()).toString();
    }

    public static Notification ticketsIssued(String orderId, String customerId, String email,
                                             String eventName, int ticketCount, Instant now) {
        return new Notification(
                idFor(orderId, Type.TICKET_ISSUED), orderId, customerId,
                Channel.EMAIL, Type.TICKET_ISSUED, email,
                "Seus ingressos para %s".formatted(eventName),
                "Pagamento aprovado. %d ingresso(s) emitido(s) para %s."
                        .formatted(ticketCount, eventName),
                Status.SENT, 1, now, now);
    }

    public static Notification paymentRejected(String orderId, String customerId, String email,
                                               String reason, Instant now) {
        return new Notification(
                idFor(orderId, Type.PAYMENT_REJECTED), orderId, customerId,
                Channel.EMAIL, Type.PAYMENT_REJECTED, email,
                "Não foi possível concluir seu pedido",
                "O pagamento não foi aprovado: %s".formatted(reason == null ? "recusado" : reason),
                Status.SENT, 1, now, now);
    }
}
