package com.ticketflow.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.notification.application.usecase.HandlePaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

import static com.ticketflow.notification.infrastructure.messaging.OrderCreatedListener.required;
import static com.ticketflow.notification.infrastructure.messaging.OrderCreatedListener.text;

/**
 * Consumes the payment outcome and issues the tickets.
 *
 * <p>Only the two fixed Portuguese event types are accepted. Anything else on this
 * topic is a contract violation upstream: it throws, and the binder routes it to the
 * DLQ rather than retrying something that will never parse.
 */
@Component("paymentProcessed")
public class PaymentProcessedListener implements Consumer<Message<String>> {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessedListener.class);

    static final String APPROVED = "PAGAMENTO_APROVADO";
    static final String REJECTED = "PAGAMENTO_RECUSADO";

    private final HandlePaymentResult handlePaymentResult;
    private final ObjectMapper objectMapper;

    public PaymentProcessedListener(HandlePaymentResult handlePaymentResult, ObjectMapper objectMapper) {
        this.handlePaymentResult = handlePaymentResult;
        this.objectMapper = objectMapper;
    }

    @Override
    public void accept(Message<String> message) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            throw new IllegalArgumentException("Payment event is not valid JSON", e);
        }

        String eventType = text(envelope, "eventType");
        boolean approved = switch (eventType) {
            case APPROVED -> true;
            case REJECTED -> false;
            case null, default -> throw new IllegalArgumentException(
                    "Unexpected eventType on the payments topic: " + eventType);
        };

        JsonNode data = envelope.path("data");
        HandlePaymentResult.Command command = new HandlePaymentResult.Command(
                required(envelope, "eventId"),
                required(data, "orderId"),
                approved,
                approved ? null : reasonOf(data));

        HandlePaymentResult.Result result = handlePaymentResult.execute(command);
        log.info("Order {} handled as {}", command.orderId(), result);
    }

    private static String reasonOf(JsonNode data) {
        String reason = text(data, "failureReason");
        String code = text(data, "failureCode");
        if (reason != null && code != null) {
            return "%s (%s)".formatted(reason, code);
        }
        return reason != null ? reason : code;
    }
}
