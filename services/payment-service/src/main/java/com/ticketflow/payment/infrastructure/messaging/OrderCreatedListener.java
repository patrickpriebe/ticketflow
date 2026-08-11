package com.ticketflow.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.payment.application.port.in.ProcessOrderPaymentUseCase;
import com.ticketflow.payment.domain.model.Money;
import com.ticketflow.payment.domain.model.Payer;
import com.ticketflow.payment.domain.model.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Consumes {@code ticketflow.orders.created} and charges the order.
 *
 * <p>The bean name gives the binding its name: {@code orderCreated-in-0}.
 *
 * <p>Parsing the envelope happens here so the use case only ever sees domain values.
 * A payload that cannot be understood throws, and the binder routes it to the DLQ -
 * retrying a malformed message would never succeed.
 */
@Component("orderCreated")
public class OrderCreatedListener implements Consumer<Message<String>> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);
    private static final String EXPECTED_TYPE = "ORDER_CREATED";

    private final ProcessOrderPaymentUseCase processOrderPayment;
    private final ObjectMapper objectMapper;

    public OrderCreatedListener(ProcessOrderPaymentUseCase processOrderPayment, ObjectMapper objectMapper) {
        this.processOrderPayment = processOrderPayment;
        this.objectMapper = objectMapper;
    }

    @Override
    public void accept(Message<String> message) {
        ProcessOrderPaymentUseCase.Command command = parse(message.getPayload());

        ProcessOrderPaymentUseCase.Result result = processOrderPayment.execute(command);

        log.info("Order {} payment result: {}", command.orderId(), result);
    }

    private ProcessOrderPaymentUseCase.Command parse(String payload) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("ORDER_CREATED is not valid JSON", e);
        }

        String eventType = text(envelope, "eventType");
        if (!EXPECTED_TYPE.equals(eventType)) {
            throw new IllegalArgumentException(
                    "Unexpected eventType on the orders topic: " + eventType);
        }

        JsonNode data = envelope.path("data");
        if (data.isMissingNode()) {
            throw new IllegalArgumentException("ORDER_CREATED has no data payload");
        }

        return new ProcessOrderPaymentUseCase.Command(
                UUID.fromString(required(envelope, "eventId")),
                UUID.fromString(required(data, "orderId")),
                new Payer(
                        UUID.fromString(required(data, "customerId")),
                        text(data, "customerName"),
                        required(data, "customerEmail")),
                Money.of(new BigDecimal(required(data, "totalAmount")), required(data, "currency")),
                PaymentMethod.valueOf(required(data, "paymentMethod")));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ORDER_CREATED is missing '%s'".formatted(field));
        }
        return value;
    }
}
