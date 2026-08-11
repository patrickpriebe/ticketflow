package com.ticketflow.order.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.order.application.port.in.ApplyPaymentResultUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Consumes {@code ticketflow.payments.processed} and finishes the order.
 *
 * <p>The bean is named {@code paymentProcessed} because Spring Cloud Stream derives
 * the binding name from it: {@code paymentProcessed-in-0}.
 *
 * <p>Reading the envelope is this class's job, not the use case's - the use case
 * receives a command made of domain values and never learns that JSON exists. A
 * message that cannot be understood throws, and the binder routes it to the DLQ
 * instead of retrying a payload that will never parse.
 */
@Component("paymentProcessed")
public class PaymentResultListener implements Consumer<Message<String>> {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    static final String APPROVED = "PAGAMENTO_APROVADO";
    static final String REJECTED = "PAGAMENTO_RECUSADO";

    private final ApplyPaymentResultUseCase applyPaymentResult;
    private final ObjectMapper objectMapper;

    public PaymentResultListener(ApplyPaymentResultUseCase applyPaymentResult, ObjectMapper objectMapper) {
        this.applyPaymentResult = applyPaymentResult;
        this.objectMapper = objectMapper;
    }

    @Override
    public void accept(Message<String> message) {
        ApplyPaymentResultUseCase.Command command = parse(message.getPayload());

        ApplyPaymentResultUseCase.Result result = applyPaymentResult.execute(command);

        if (result == ApplyPaymentResultUseCase.Result.IGNORED_DUPLICATE) {
            // Expected, not exceptional: at-least-once delivery guarantees this
            // happens eventually.
            log.info("Ignored duplicate payment event {} for order {}",
                    command.eventId(), command.orderId());
        } else {
            log.info("Order {} settled as {} by event {}", command.orderId(),
                    command.approved() ? "PAID" : "REJECTED", command.eventId());
        }
    }

    private ApplyPaymentResultUseCase.Command parse(String payload) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Payment event is not valid JSON", e);
        }

        String eventType = text(envelope, "eventType");
        boolean approved = switch (eventType) {
            case APPROVED -> true;
            case REJECTED -> false;
            // Anything else on this topic is a contract violation upstream. Retrying
            // will not help, so fail and let it land in the DLQ.
            case null, default -> throw new IllegalArgumentException(
                    "Unexpected eventType on the payment topic: " + eventType);
        };

        JsonNode data = envelope.path("data");
        if (data.isMissingNode()) {
            throw new IllegalArgumentException("Payment event has no data payload");
        }

        return new ApplyPaymentResultUseCase.Command(
                UUID.fromString(required(envelope, "eventId")),
                UUID.fromString(required(data, "orderId")),
                approved,
                approved ? null : failureReasonOf(data),
                Instant.parse(required(envelope, "occurredAt")));
    }

    private static String failureReasonOf(JsonNode data) {
        String reason = text(data, "failureReason");
        String code = text(data, "failureCode");
        if (reason != null && code != null) {
            return "%s (%s)".formatted(reason, code);
        }
        return reason != null ? reason : code;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payment event is missing '%s'".formatted(field));
        }
        return value;
    }
}
