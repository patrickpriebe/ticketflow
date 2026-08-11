package com.ticketflow.order.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketflow.order.support.OrderServiceIT;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The asynchronous loop, end to end, in one process: HTTP in, Kafka out, Kafka back
 * in, order settled.
 *
 * <p>The broker is an in-process EmbeddedKafka, so this runs with no Docker at all.
 * The relay's timer is switched off and {@code dispatchBatch()} is called explicitly
 * - a test that raced a background scheduler would be flaky by construction.
 */
@SpringBootTest(properties = {
        "ticketflow.outbox.scheduling-enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.cloud.stream.kafka.bindings.paymentProcessed-in-0.consumer.start-offset=earliest"
})
@EmbeddedKafka(partitions = 1, topics = {
        OrderFlowIT.ORDERS_CREATED,
        OrderFlowIT.PAYMENTS_PROCESSED,
        OrderFlowIT.PAYMENTS_DLQ
})
@AutoConfigureMockMvc
class OrderFlowIT extends OrderServiceIT {

    static final String ORDERS_CREATED = "ticketflow.orders.created";
    static final String PAYMENTS_PROCESSED = "ticketflow.payments.processed";
    static final String PAYMENTS_DLQ = "ticketflow.payments.processed.dlq";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutboxRelay outboxRelay;
    @Autowired
    private EmbeddedKafkaBroker broker;

    private Consumer<String, String> consumer;

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
    }

    private Consumer<String, String> consumerFor(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                broker.getBrokersAsString(), "it-" + UUID.randomUUID(), "true");
        consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    private KafkaTemplate<String, String> producer() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker.getBrokersAsString()),
                new StringSerializer(), new StringSerializer()));
    }

    private UUID placeOrder(UUID categoryId, int quantity) throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(categoryId, quantity)))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String paymentEvent(UUID eventId, UUID orderId, boolean approved) {
        String type = approved ? "PAGAMENTO_APROVADO" : "PAGAMENTO_RECUSADO";
        String outcome = approved
                ? "\"gatewayTransactionId\": \"ch_3PqR7s2eZvKYlo2C\""
                : "\"failureCode\": \"INSUFFICIENT_FUNDS\", \"failureReason\": \"Card declined by issuer\"";

        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "occurredAt": "%s",
                  "producer": "payment-service",
                  "correlationId": "%s",
                  "data": {
                    "paymentId": "%s",
                    "orderId": "%s",
                    "customerId": "%s",
                    "amount": 1300.00,
                    "currency": "BRL",
                    "method": "CREDIT_CARD",
                    "processedAt": "%s",
                    "gatewayName": "acme-payments",
                    %s,
                    "attempts": 1
                  }
                }
                """.formatted(eventId, type, Instant.now(), orderId, UUID.randomUUID(),
                orderId, CUSTOMER_ID, Instant.now(), outcome);
    }

    private void publishPaymentResult(UUID eventId, UUID orderId, boolean approved) {
        producer().send(new ProducerRecord<>(
                PAYMENTS_PROCESSED, orderId.toString(), paymentEvent(eventId, orderId, approved)));
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    @Test
    @DisplayName("the relay publishes ORDER_CREATED keyed by the order id")
    void relayPublishesOrderCreated() throws Exception {
        Consumer<String, String> records = consumerFor(ORDERS_CREATED);
        UUID orderId = placeOrder(PISTA_ID, 2);

        assertThat(outboxRelay.dispatchBatch()).isEqualTo(1);

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(records, ORDERS_CREATED, Duration.ofSeconds(20));

        // The key is what keeps every event about one order on a single partition,
        // and therefore in order.
        assertThat(record.key()).isEqualTo(orderId.toString());

        JsonNode envelope = objectMapper.readTree(record.value());
        assertThat(envelope.get("eventType").asText()).isEqualTo("ORDER_CREATED");
        assertThat(envelope.get("producer").asText()).isEqualTo("order-service");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("data").get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(envelope.get("data").get("totalAmount").asDouble()).isEqualTo(1300.00);
        assertThat(envelope.get("data").get("items").get(0).get("categoryName").asText())
                .isEqualTo("Pista");

        // Marked only after the broker accepted it.
        Map<String, Object> outbox = jdbc.queryForMap(
                "SELECT status, published_at FROM outbox_messages WHERE aggregate_id = ?", orderId);
        assertThat(outbox.get("status")).isEqualTo("PUBLISHED");
        assertThat(outbox.get("published_at")).isNotNull();
    }

    @Test
    @DisplayName("a published message is not published again on the next cycle")
    void doesNotRepublish() throws Exception {
        placeOrder(PISTA_ID, 1);

        assertThat(outboxRelay.dispatchBatch()).isEqualTo(1);
        assertThat(outboxRelay.dispatchBatch()).isZero();
    }

    @Test
    @DisplayName("PAGAMENTO_APROVADO settles the order and turns the reservation into a sale")
    void approvalSettlesOrder() throws Exception {
        UUID orderId = placeOrder(PISTA_ID, 2);

        publishPaymentResult(UUID.randomUUID(), orderId, true);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo("PAID"));

        Map<String, Object> inventory = jdbc.queryForMap(
                "SELECT reserved_quantity, sold_quantity FROM ticket_categories WHERE id = ?", PISTA_ID);
        assertThat(inventory.get("reserved_quantity")).isEqualTo(0);
        assertThat(inventory.get("sold_quantity")).isEqualTo(2);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM order_status_history WHERE order_id = ?", Integer.class, orderId))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("PAGAMENTO_RECUSADO releases the tickets back to the pool")
    void refusalReleasesInventory() throws Exception {
        UUID orderId = placeOrder(PISTA_ID, 2);

        publishPaymentResult(UUID.randomUUID(), orderId, false);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo("REJECTED"));

        Map<String, Object> inventory = jdbc.queryForMap(
                "SELECT reserved_quantity, sold_quantity FROM ticket_categories WHERE id = ?", PISTA_ID);
        // A declined card must not keep tickets off the market.
        assertThat(inventory.get("reserved_quantity")).isEqualTo(0);
        assertThat(inventory.get("sold_quantity")).isEqualTo(0);

        assertThat(jdbc.queryForObject(
                "SELECT reason FROM order_status_history WHERE order_id = ? AND to_status = 'REJECTED'",
                String.class, orderId))
                .contains("Card declined by issuer");
    }

    @Test
    @DisplayName("redelivering the same payment event changes nothing")
    void redeliveryIsIgnored() throws Exception {
        UUID orderId = placeOrder(PISTA_ID, 2);
        UUID eventId = UUID.randomUUID();

        publishPaymentResult(eventId, orderId, true);
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(orderStatus(orderId)).isEqualTo("PAID"));

        // Exactly what Kafka's at-least-once delivery will eventually do on its own.
        publishPaymentResult(eventId, orderId, true);

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(orderStatus(orderId)).isEqualTo("PAID");
            // No second transition, no double sale, one inbox row.
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM order_status_history WHERE order_id = ?", Integer.class, orderId))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT sold_quantity FROM ticket_categories WHERE id = ?", Integer.class, PISTA_ID))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM processed_events", Integer.class)).isEqualTo(1);
        });
    }
}
