package com.ticketflow.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.payment.application.port.out.DomainEventPublisher;
import com.ticketflow.payment.domain.event.PaymentSettled;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.infrastructure.persistence.entity.OutboxMessageEntity;
import com.ticketflow.payment.infrastructure.persistence.jpa.JpaOutboxRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records a settled payment in the transactional outbox.
 *
 * <p>Writes the row in whatever transaction the caller is already in - the same one
 * that updated the payment. The JSON must satisfy
 * {@code contracts/events/payment-processed.v1.schema.json}.
 */
@Component
public class OutboxEventPublisher implements DomainEventPublisher {

    static final String TOPIC_PAYMENTS_PROCESSED = "ticketflow.payments.processed";
    private static final String AGGREGATE_TYPE = "Payment";
    private static final String PRODUCER = "payment-service";
    private static final int EVENT_VERSION = 1;

    private final JpaOutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventPublisher(JpaOutboxRepository outbox, ObjectMapper objectMapper, Clock clock) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publish(PaymentSettled event) {
        Payment payment = event.payment();

        outbox.save(new OutboxMessageEntity(
                UUID.randomUUID(),
                AGGREGATE_TYPE,
                payment.id(),
                event.eventType(),
                TOPIC_PAYMENTS_PROCESSED,
                // Keyed by orderId, not paymentId: it keeps this event on the same
                // partition as the ORDER_CREATED it answers, so consumers see them in
                // order.
                payment.orderId().toString(),
                writeJson(envelopeOf(event)),
                writeJson(Map.of("contentType", "application/json", "eventType", event.eventType())),
                clock.instant()));
    }

    private Map<String, Object> envelopeOf(PaymentSettled event) {
        Payment payment = event.payment();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId().toString());
        envelope.put("eventType", event.eventType());
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("producer", PRODUCER);
        envelope.put("correlationId", payment.orderId().toString());
        envelope.put("data", dataOf(event));
        return envelope;
    }

    private Map<String, Object> dataOf(PaymentSettled event) {
        Payment payment = event.payment();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentId", payment.id().toString());
        data.put("orderId", payment.orderId().toString());
        data.put("customerId", payment.customerId().toString());
        data.put("amount", payment.amount().amount());
        data.put("currency", payment.amount().currency());
        data.put("method", payment.method().name());
        data.put("processedAt", event.occurredAt().toString());
        data.put("gatewayName", payment.gatewayName());
        data.put("attempts", payment.attempts());

        // The schema requires a transaction id when approved and a failure code when
        // refused - never both.
        if (payment.isApproved()) {
            data.put("gatewayTransactionId", payment.gatewayTransactionId());
        } else {
            data.put("failureCode", payment.failureCode());
            data.put("failureReason", payment.failureReason());
        }
        return data;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }
}
