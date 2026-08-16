package com.ticketflow.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketflow.payment.support.PaymentServiceIT;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The Payment Service's whole loop in one process: ORDER_CREATED in, gateway called,
 * PAGAMENTO_APROVADO or PAGAMENTO_RECUSADO out.
 *
 * <p>EmbeddedKafka for the broker and an in-process Wiremock for the provider, so
 * this needs no Docker beyond the database. The relay's timer is off and
 * {@code dispatchBatch()} is called explicitly - racing a scheduler makes a test
 * flaky by construction.
 */
@SpringBootTest(properties = {
        "ticketflow.outbox.scheduling-enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.cloud.stream.kafka.bindings.orderCreated-in-0.consumer.start-offset=earliest",
        // Sem isto o contexto nem sobe: o SecurityConfiguration derruba o boot
        // quando não há issuer nem segredo, e é para fazer exatamente isso.
        //
        // Esta linha faltava desde que a camada de autenticação entrou neste
        // serviço, junto com o endpoint que devolve o client_secret para o
        // Stripe Elements — o IT é anterior a ela e ninguém voltou aqui. O CI
        // ficou vermelho desde então, e passou despercebido porque `mvnw test`
        // só roda os unitários; quem exercita isto é o `verify`.
        "ticketflow.auth.secret=segredo-de-teste-com-mais-de-32-caracteres"
})
@EmbeddedKafka(partitions = 1, topics = {
        PaymentFlowIT.ORDERS_CREATED,
        PaymentFlowIT.ORDERS_DLQ,
        PaymentFlowIT.PAYMENTS_PROCESSED,
        // O consumidor de cancelamento é novo, e o binder tem
        // `auto-create-topics: false` — um tópico não declarado aqui não nasce.
        PaymentFlowIT.ORDERS_CANCELLED,
        PaymentFlowIT.ORDERS_CANCELLED_DLQ
})
class PaymentFlowIT extends PaymentServiceIT {

    static final String ORDERS_CREATED = "ticketflow.orders.created";
    static final String ORDERS_DLQ = "ticketflow.orders.created.dlq";
    static final String PAYMENTS_PROCESSED = "ticketflow.payments.processed";
    static final String ORDERS_CANCELLED = "ticketflow.orders.cancelled";
    static final String ORDERS_CANCELLED_DLQ = "ticketflow.orders.cancelled.dlq";

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
        consumer = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps(broker.getBrokersAsString(), "it-" + UUID.randomUUID(), "true"),
                new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, topic);
        return consumer;
    }

    private void publishOrderCreated(UUID eventId, UUID orderId, String amount) {
        String payload = """
                {
                  "eventId": "%s",
                  "eventType": "ORDER_CREATED",
                  "eventVersion": 1,
                  "occurredAt": "%s",
                  "producer": "order-service",
                  "correlationId": "%s",
                  "data": {
                    "orderId": "%s",
                    "customerId": "3f1c9a6e-77b2-4c0d-9f31-2a5b8e4d6c10",
                    "customerName": "Ana Souza",
                    "customerEmail": "ana.souza@example.com",
                    "eventId": "11111111-1111-4111-8111-111111111111",
                    "eventName": "Rock in Rio 2026 - Dia 1",
                    "paymentMethod": "CREDIT_CARD",
                    "totalAmount": %s,
                    "currency": "BRL",
                    "items": [
                      {"ticketCategoryId":"aaaaaaaa-0001-4000-8000-000000000001",
                       "categoryName":"Pista","quantity":2,"unitPrice":650.00}
                    ]
                  }
                }
                """.formatted(eventId, Instant.now(), orderId, orderId, amount);

        KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker.getBrokersAsString()),
                new StringSerializer(), new StringSerializer()));
        try {
            // Synchronous: an async send that silently failed would show up as
            // "the consumer never ran", sending the investigation the wrong way.
            template.send(new ProducerRecord<>(ORDERS_CREATED, orderId.toString(), payload))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao publicar ORDER_CREATED no teste", e);
        } finally {
            template.destroy();
        }
    }

    private void gatewayApproves() {
        GATEWAY.stubFor(post(urlEqualTo("/v1/charges")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"approved\",\"transactionId\":\"ch_it_12345\"}")));
    }

    private void gatewayDeclines() {
        GATEWAY.stubFor(post(urlEqualTo("/v1/charges")).willReturn(aResponse()
                .withStatus(402)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"declined\",\"code\":\"INSUFFICIENT_FUNDS\","
                        + "\"message\":\"Card declined by issuer\"}")));
    }

    /**
     * Returns null while the payment does not exist yet.
     *
     * <p>Deliberately not {@code queryForObject}: that throws
     * {@code EmptyResultDataAccessException} on no rows, and Awaitility only retries
     * on {@code AssertionError} - so the wait would abort on the first poll instead
     * of giving the consumer time to run.
     */
    private String paymentStatus(UUID orderId) {
        List<String> rows = jdbc.queryForList(
                "SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void awaitPaymentStatus(UUID orderId, String expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(paymentStatus(orderId)).isEqualTo(expected));
    }

    @Test
    @DisplayName("an approved charge settles the payment and queues PAGAMENTO_APROVADO")
    void approvedFlow() {
        gatewayApproves();
        UUID orderId = UUID.randomUUID();

        publishOrderCreated(UUID.randomUUID(), orderId, "1300.00");

        awaitPaymentStatus(orderId, "APPROVED");

        Map<String, Object> outbox = jdbc.queryForMap(
                "SELECT event_type, topic, partition_key, status FROM outbox_messages");
        assertThat(outbox.get("event_type")).isEqualTo("PAGAMENTO_APROVADO");
        assertThat(outbox.get("topic")).isEqualTo(PAYMENTS_PROCESSED);
        // Keyed by orderId so this answer stays on the same partition as the
        // ORDER_CREATED it responds to.
        assertThat(outbox.get("partition_key")).isEqualTo(orderId.toString());

        assertThat(jdbc.queryForObject(
                "SELECT outcome FROM payment_attempts", String.class)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("the relay publishes the settled payment onto the topic")
    void relayPublishesResult() throws Exception {
        Consumer<String, String> records = consumerFor(PAYMENTS_PROCESSED);
        gatewayApproves();
        UUID orderId = UUID.randomUUID();

        publishOrderCreated(UUID.randomUUID(), orderId, "1300.00");
        awaitPaymentStatus(orderId, "APPROVED");

        assertThat(outboxRelay.dispatchBatch()).isEqualTo(1);

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(records, PAYMENTS_PROCESSED, Duration.ofSeconds(20));

        assertThat(record.key()).isEqualTo(orderId.toString());

        JsonNode envelope = objectMapper.readTree(record.value());
        assertThat(envelope.get("eventType").asText()).isEqualTo("PAGAMENTO_APROVADO");
        assertThat(envelope.get("producer").asText()).isEqualTo("payment-service");
        assertThat(envelope.get("data").get("orderId").asText()).isEqualTo(orderId.toString());
        // The schema requires a transaction id whenever the payment was approved.
        assertThat(envelope.get("data").get("gatewayTransactionId").asText()).isEqualTo("ch_it_12345");
    }

    @Test
    @DisplayName("a declined charge queues PAGAMENTO_RECUSADO carrying the reason")
    void declinedFlow() {
        gatewayDeclines();
        UUID orderId = UUID.randomUUID();

        publishOrderCreated(UUID.randomUUID(), orderId, "2400.00");

        awaitPaymentStatus(orderId, "REJECTED");

        Map<String, Object> outbox = jdbc.queryForMap(
                "SELECT event_type, payload FROM outbox_messages");
        assertThat(outbox.get("event_type")).isEqualTo("PAGAMENTO_RECUSADO");
        assertThat(String.valueOf(outbox.get("payload"))).contains("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("the gateway is called with the payment id as idempotency key")
    void sendsIdempotencyKey() {
        gatewayApproves();
        UUID orderId = UUID.randomUUID();

        publishOrderCreated(UUID.randomUUID(), orderId, "1300.00");
        awaitPaymentStatus(orderId, "APPROVED");

        String paymentId = jdbc.queryForObject(
                "SELECT id::text FROM payments WHERE order_id = ?", String.class, orderId);
        GATEWAY.verify(postRequestedFor(urlEqualTo("/v1/charges"))
                .withHeader("Idempotency-Key", equalTo(paymentId)));
    }

    @Test
    @DisplayName("a redelivered ORDER_CREATED never charges the customer twice")
    void redeliveryDoesNotChargeTwice() {
        gatewayApproves();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        publishOrderCreated(eventId, orderId, "1300.00");
        awaitPaymentStatus(orderId, "APPROVED");

        // Exactly what at-least-once delivery does on its own, sooner or later.
        publishOrderCreated(eventId, orderId, "1300.00");

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
            // One call to the gateway, one attempt, one event to publish.
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_attempts", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages", Integer.class)).isEqualTo(1);
        });
        GATEWAY.verify(1, postRequestedFor(urlEqualTo("/v1/charges")));
    }

    @Test
    @DisplayName("a gateway 5xx leaves the payment FAILED and announces nothing")
    void serverErrorAnnouncesNothing() {
        GATEWAY.stubFor(post(urlEqualTo("/v1/charges")).willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"upstream unavailable\"}")));
        UUID orderId = UUID.randomUUID();

        publishOrderCreated(UUID.randomUUID(), orderId, "1300.00");

        awaitPaymentStatus(orderId, "FAILED");

        // Nothing is announced and nothing is marked as processed: saying the card
        // was declined when the gateway never answered would be a lie, and the
        // message must stay eligible for redelivery.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT outcome FROM payment_attempts LIMIT 1", String.class))
                .isEqualTo("ERROR");
    }
}
