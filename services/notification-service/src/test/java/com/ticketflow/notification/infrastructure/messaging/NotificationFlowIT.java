package com.ticketflow.notification.infrastructure.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The Notification Service's loop end to end: ORDER_CREATED builds the read model,
 * the payment result turns it into tickets.
 *
 * <p>Written after debugging this through containers proved slow and uninformative -
 * a silent consumer looks identical to a broken one from the outside. In here a
 * failure is a stack trace in seconds.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {
        NotificationFlowIT.ORDERS_CREATED,
        NotificationFlowIT.ORDERS_DLQ,
        NotificationFlowIT.PAYMENTS_PROCESSED,
        NotificationFlowIT.PAYMENTS_DLQ
})
class NotificationFlowIT {

    static final String ORDERS_CREATED = "ticketflow.orders.created";
    static final String ORDERS_DLQ = "ticketflow.orders.created.dlq";
    static final String PAYMENTS_PROCESSED = "ticketflow.payments.processed";
    static final String PAYMENTS_DLQ = "ticketflow.payments.processed.dlq";

    /**
     * MongoDB de verdade, via Testcontainers.
     *
     * <p>Na CI, em Linux, o Testcontainers funciona normalmente. Na máquina de
     * desenvolvimento, onde o Docker não expõe a Engine API para clientes JVM,
     * passe {@code -Dticketflow.it.mongo.uri=...} apontando para o Mongo do
     * compose.
     */
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongo(DynamicPropertyRegistry registry) {
        String external = System.getProperty("ticketflow.it.mongo.uri");
        if (external != null && !external.isBlank()) {
            registry.add("spring.data.mongodb.uri", () -> external);
            return;
        }
        MONGO.start();
        registry.add("spring.data.mongodb.uri",
                () -> MONGO.getReplicaSetUrl("ticketflow_notifications_it"));
    }

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private MongoTemplate mongo;

    private String orderId;

    /**
     * Each test gets its own order id and asserts only on documents belonging to it.
     *
     * <p>Wiping the collections between tests looks tidier and is actively harmful
     * here: the consumer group survives across tests, so a previous test's payment
     * event may still be in flight. Deleting its snapshot turns that message into a
     * poison pill that fails forever and blocks the partition, starving every test
     * that follows - which is precisely the failure this suite was written to
     * explain.
     */
    @BeforeEach
    void newOrder() {
        orderId = UUID.randomUUID().toString();
    }

    private void publish(String topic, String payload) {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker.getBrokersAsString()),
                new StringSerializer(), new StringSerializer()));
        try {
            template.send(new ProducerRecord<>(topic, orderId, payload)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("falha ao publicar em " + topic, e);
        } finally {
            template.destroy();
        }
    }

    private void publishOrderCreated(int quantity) {
        publish(ORDERS_CREATED, """
                {
                  "eventId": "%s", "eventType": "ORDER_CREATED", "eventVersion": 1,
                  "occurredAt": "%s", "producer": "order-service", "correlationId": "%s",
                  "data": {
                    "orderId": "%s",
                    "customerId": "3f1c9a6e-77b2-4c0d-9f31-2a5b8e4d6c10",
                    "customerName": "Ana Souza",
                    "customerEmail": "ana.souza@example.com",
                    "eventId": "11111111-1111-4111-8111-111111111111",
                    "eventName": "Rock in Rio 2026 - Dia 1",
                    "paymentMethod": "CREDIT_CARD", "totalAmount": 1300.00, "currency": "BRL",
                    "items": [{"ticketCategoryId":"cat-pista","categoryName":"Pista",
                               "quantity":%d,"unitPrice":650.00}]
                  }
                }
                """.formatted(UUID.randomUUID(), Instant.now(), orderId, orderId, quantity));
    }

    private void publishPaymentResult(String eventId, boolean approved) {
        String type = approved ? "PAGAMENTO_APROVADO" : "PAGAMENTO_RECUSADO";
        String outcome = approved
                ? "\"gatewayTransactionId\": \"ch_it\""
                : "\"failureCode\": \"INSUFFICIENT_FUNDS\", \"failureReason\": \"Card declined by issuer\"";

        publish(PAYMENTS_PROCESSED, """
                {
                  "eventId": "%s", "eventType": "%s", "eventVersion": 1,
                  "occurredAt": "%s", "producer": "payment-service", "correlationId": "%s",
                  "data": {
                    "paymentId": "%s", "orderId": "%s",
                    "customerId": "3f1c9a6e-77b2-4c0d-9f31-2a5b8e4d6c10",
                    "amount": 1300.00, "currency": "BRL", "method": "CREDIT_CARD",
                    "processedAt": "%s", "gatewayName": "acme-payments", %s, "attempts": 1
                  }
                }
                """.formatted(eventId, type, Instant.now(), orderId,
                UUID.randomUUID(), orderId, Instant.now(), outcome));
    }

    /** Scoped to this test's order, so tests never see each other's documents. */
    private long count(String collection) {
        String field = "order_snapshots".equals(collection) ? "_id" : "orderId";
        return mongo.getCollection(collection)
                .countDocuments(new org.bson.Document(field, orderId));
    }

    private void awaitCount(String collection, long expected) {
        await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(count(collection)).isEqualTo(expected));
    }

    private org.bson.Document findOne(String collection) {
        return mongo.getCollection(collection).find(new org.bson.Document("orderId", orderId)).first();
    }

    @Test
    @DisplayName("ORDER_CREATED builds the read model keyed by the order id")
    void buildsReadModel() {
        publishOrderCreated(2);

        // The _id must be the orderId itself: without that the lookup by id never
        // matches and every redelivery inserts another copy. Counting by _id only
        // returns 1 if the mapping is right.
        awaitCount("order_snapshots", 1);
    }

    @Test
    @DisplayName("an approved payment issues one ticket per unit and notifies the customer")
    void issuesTickets() {
        publishOrderCreated(2);
        awaitCount("order_snapshots", 1);

        publishPaymentResult(UUID.randomUUID().toString(), true);

        awaitCount("tickets", 2);
        awaitCount("notifications", 1);

        var ticket = findOne("tickets");
        assertThat(String.valueOf(ticket.get("ticketCode"))).matches("^TF-[A-Z0-9]{10}$");
        assertThat(ticket.get("status")).isEqualTo("ISSUED");

        // The stored shape has to match what the collection validator demands. This
        // test database has no validators, so without asserting it here a mapping
        // change passes green and only fails against a real environment - which is
        // exactly how the nested `id` -> `_id` promotion got through once already.
        var category = (org.bson.Document) ticket.get("ticketCategory");
        assertThat(category.keySet()).contains("categoryId", "name").doesNotContain("_id", "id");
        var holder = (org.bson.Document) ticket.get("holder");
        assertThat(holder.keySet()).contains("customerId", "email").doesNotContain("_id");
    }

    @Test
    @DisplayName("a refused payment notifies and issues nothing")
    void refusalIssuesNothing() {
        publishOrderCreated(2);
        awaitCount("order_snapshots", 1);

        publishPaymentResult(UUID.randomUUID().toString(), false);

        awaitCount("notifications", 1);
        // The one thing that must never happen when the payment was refused.
        assertThat(count("tickets")).isZero();
    }

    @Test
    @DisplayName("redelivering the payment event does not issue a second set of tickets")
    void redeliveryIsIdempotent() {
        publishOrderCreated(2);
        awaitCount("order_snapshots", 1);

        String eventId = UUID.randomUUID().toString();
        publishPaymentResult(eventId, true);
        awaitCount("tickets", 2);

        publishPaymentResult(eventId, true);

        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            // Deterministic ids mean the replay rewrites the same documents rather
            // than minting a second set of tickets for an order already served.
            assertThat(count("tickets")).isEqualTo(2);
            assertThat(count("notifications")).isEqualTo(1);
        });
    }
}
