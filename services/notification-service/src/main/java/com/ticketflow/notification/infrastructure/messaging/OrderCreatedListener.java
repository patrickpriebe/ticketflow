package com.ticketflow.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.notification.application.port.out.Repositories;
import com.ticketflow.notification.domain.model.OrderSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds this service's read model from ORDER_CREATED.
 *
 * <p>The payment event says how much was charged, never what was bought. Rather than
 * asking the Order Service - a synchronous call this architecture does not allow -
 * the service listens to the same stream and keeps the little it needs.
 *
 * <p>Both topics are keyed by orderId, so a given order's events stay ordered
 * relative to each other and the snapshot reliably arrives before its payment.
 */
@Component("orderCreated")
public class OrderCreatedListener implements Consumer<Message<String>> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final Repositories.OrderSnapshots snapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OrderCreatedListener(Repositories.OrderSnapshots snapshots,
                                ObjectMapper objectMapper,
                                Clock clock) {
        this.snapshots = snapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void accept(Message<String> message) {
        JsonNode envelope = read(message.getPayload());

        if (!"ORDER_CREATED".equals(text(envelope, "eventType"))) {
            throw new IllegalArgumentException(
                    "Unexpected eventType on the orders topic: " + text(envelope, "eventType"));
        }

        JsonNode data = envelope.path("data");
        List<OrderSnapshot.Line> items = new ArrayList<>();
        for (JsonNode item : data.path("items")) {
            items.add(new OrderSnapshot.Line(
                    text(item, "ticketCategoryId"),
                    text(item, "categoryName"),
                    item.path("quantity").asInt()));
        }

        OrderSnapshot snapshot = new OrderSnapshot(
                required(data, "orderId"),
                text(data, "customerId"),
                text(data, "customerName"),
                required(data, "customerEmail"),
                text(data, "eventId"),
                text(data, "eventName"),
                items,
                clock.instant());

        snapshots.save(snapshot);
        log.debug("Stored snapshot for order {} with {} ticket(s)",
                snapshot.orderId(), snapshot.totalTickets());
    }

    private JsonNode read(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("ORDER_CREATED is not valid JSON", e);
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Event is missing '%s'".formatted(field));
        }
        return value;
    }
}
