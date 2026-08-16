package com.ticketflow.order.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.order.application.port.out.DomainEventPublisher;
import com.ticketflow.order.domain.event.OrderCancelled;
import com.ticketflow.order.domain.event.OrderCreated;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.infrastructure.persistence.entity.OutboxMessageEntity;
import com.ticketflow.order.infrastructure.persistence.jpa.JpaOutboxRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Driven adapter: records a domain event in the transactional outbox.
 *
 * <p>Nothing is sent to Kafka here. The row is written in whatever transaction the
 * caller is already in - the same one that inserts the order - and a separate relay
 * publishes it afterwards. That ordering is what guarantees an order and its event
 * can never disagree about whether they happened.
 *
 * <p>The JSON produced must satisfy
 * {@code contracts/events/order-created.v1.schema.json}.
 */
@Component
public class OutboxEventPublisher implements DomainEventPublisher {

    static final String TOPIC_ORDERS_CREATED = "ticketflow.orders.created";
    static final String TOPIC_ORDERS_CANCELLED = "ticketflow.orders.cancelled";
    private static final String AGGREGATE_TYPE = "Order";
    private static final String PRODUCER = "order-service";
    private static final int EVENT_VERSION = 1;

    private final JpaOutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(JpaOutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(OrderCreated event) {
        Order order = event.order();

        OutboxMessageEntity message = new OutboxMessageEntity(
                UUID.randomUUID(),
                AGGREGATE_TYPE,
                order.id(),
                OrderCreated.TYPE,
                TOPIC_ORDERS_CREATED,
                // Keying by orderId keeps every event about one order on the same
                // partition, and therefore strictly ordered.
                order.id().toString(),
                writeJson(envelopeOf(event)),
                writeJson(Map.of("contentType", "application/json", "eventType", OrderCreated.TYPE)),
                Instant.now());

        outbox.save(message);
    }

    @Override
    public void publish(OrderCancelled event) {
        Order order = event.order();

        OutboxMessageEntity message = new OutboxMessageEntity(
                UUID.randomUUID(),
                AGGREGATE_TYPE,
                order.id(),
                OrderCancelled.TYPE,
                TOPIC_ORDERS_CANCELLED,
                // Mesma chave do ORDER_CREATED daquele pedido. Não é para ordenar
                // entre tópicos — isso o Kafka não garante —, é para o Payment
                // Service continuar lendo tudo de um pedido na mesma partição.
                order.id().toString(),
                writeJson(cancelledEnvelope(event)),
                writeJson(Map.of("contentType", "application/json", "eventType", OrderCancelled.TYPE)),
                Instant.now());

        outbox.save(message);
    }

    private Map<String, Object> cancelledEnvelope(OrderCancelled event) {
        Order order = event.order();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.id().toString());
        data.put("customerId", order.customer().id().toString());
        data.put("reason", event.reason());
        // O valor vai junto porque quem estorna precisa saber quanto devolver sem
        // perguntar a ninguém. Consultar o Order Service para descobrir seria a
        // chamada síncrona entre serviços que este projeto não admite.
        data.put("totalAmount", order.totalAmount().amount());
        data.put("currency", order.totalAmount().currency());
        // O método viaja pelo mesmo motivo, e resolve um caso específico: quando o
        // cancelamento chega ANTES do ORDER_CREATED, o Payment Service registra a
        // cobrança já cancelada para que ela nunca seja feita — e uma cobrança,
        // mesmo natimorta, precisa dizer por qual meio seria.
        data.put("paymentMethod", order.paymentMethod().name());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId().toString());
        envelope.put("eventType", OrderCancelled.TYPE);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("producer", PRODUCER);
        envelope.put("correlationId", order.id().toString());
        envelope.put("data", data);
        return envelope;
    }

    private Map<String, Object> envelopeOf(OrderCreated event) {
        Order order = event.order();

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", event.eventId().toString());
        envelope.put("eventType", OrderCreated.TYPE);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", event.occurredAt().toString());
        envelope.put("producer", PRODUCER);
        envelope.put("correlationId", order.id().toString());
        envelope.put("data", dataOf(event));
        return envelope;
    }

    private Map<String, Object> dataOf(OrderCreated event) {
        Order order = event.order();

        List<Map<String, Object>> items = order.items().stream()
                .map(item -> {
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("ticketCategoryId", item.ticketCategoryId().toString());
                    line.put("categoryName", item.categoryName());
                    line.put("quantity", item.quantity());
                    line.put("unitPrice", item.unitPrice().amount());
                    return line;
                })
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", order.id().toString());
        data.put("customerId", order.customer().id().toString());
        data.put("customerName", order.customer().name());
        data.put("customerEmail", order.customer().email());
        // "eventId" inside data is the SHOW, not the message. The envelope's eventId
        // is the message identity. Both names come from the contract.
        data.put("eventId", order.ticketEventId().toString());
        data.put("eventName", event.ticketEventName());
        data.put("paymentMethod", order.paymentMethod().name());
        data.put("totalAmount", order.totalAmount().amount());
        data.put("currency", order.totalAmount().currency());
        data.put("items", items);
        return data;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            // Serialising a map of strings and numbers cannot realistically fail; if
            // it does, the order must not be committed with an unpublishable event.
            throw new IllegalStateException("Failed to serialise outbox payload", e);
        }
    }
}
