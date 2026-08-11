package com.ticketflow.order.infrastructure.web;

import com.ticketflow.order.support.OrderServiceIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract, end to end through the real mapping layer and a real
 * PostgreSQL. Flyway applies the same migrations production will run, so a mismatch
 * between the entities and the schema fails here rather than at deploy time.
 *
 * <p>The outbox relay is switched off here so the outbox row can be asserted exactly
 * as it is written. Publishing is {@code OrderFlowIT}'s subject.
 */
@SpringBootTest(properties = {
        "ticketflow.outbox.scheduling-enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = {
        "ticketflow.orders.created",
        "ticketflow.payments.processed",
        "ticketflow.payments.processed.dlq"
})
@AutoConfigureMockMvc
class OrderControllerIT extends OrderServiceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /orders is accepted immediately as PENDING, with a Location to poll")
    void acceptsOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(PISTA_ID, 2)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount.amount").value(1300.00))
                .andExpect(jsonPath("$.totalAmount.currency").value("BRL"))
                .andExpect(jsonPath("$.items[0].categoryName").value("Pista"))
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("PENDING"));
    }

    @Test
    @DisplayName("the order and its ORDER_CREATED event are written in the same transaction")
    void writesOutboxAtomically() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(PISTA_ID, 2)))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID orderId = UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id").asText());

        // This is the assertion that proves the transactional outbox: the order row
        // and its event row are both there, committed together.
        Map<String, Object> outbox = jdbc.queryForMap(
                "SELECT event_type, topic, partition_key, status FROM outbox_messages WHERE aggregate_id = ?",
                orderId);

        assertThat(outbox.get("event_type")).isEqualTo("ORDER_CREATED");
        assertThat(outbox.get("topic")).isEqualTo("ticketflow.orders.created");
        // Keyed by orderId so every event about this order stays on one partition.
        assertThat(outbox.get("partition_key")).isEqualTo(orderId.toString());
        assertThat(outbox.get("status")).isEqualTo("PENDING");

        assertThat(jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("replaying the same Idempotency-Key returns the original order, not a second one")
    void replayIsIdempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = orderBody(PISTA_ID, 2);

        MvcResult first = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        // Same key again - as a client would after a network timeout.
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages", Integer.class)).isEqualTo(1);
        // Inventory was held once, not twice.
        assertThat(jdbc.queryForObject(
                "SELECT reserved_quantity FROM ticket_categories WHERE id = ?", Integer.class, PISTA_ID))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("overselling is refused with 409 and an RFC 7807 body")
    void refusesToOversell() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(CAMAROTE_ID, 4)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ticketflow.dev/problems/insufficient-inventory"))
                .andExpect(jsonPath("$.title").value("Insufficient inventory"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages", Integer.class)).isZero();
    }

    @Test
    @DisplayName("a request without Idempotency-Key is rejected with 400")
    void requiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(PISTA_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://ticketflow.dev/problems/missing-header"));
    }

    @Test
    @DisplayName("an unknown order is a 404 problem, not an empty 200")
    void unknownOrderIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://ticketflow.dev/problems/order-not-found"));
    }

    @Test
    @DisplayName("GET /events lists the catalogue with the cheapest price")
    void listsCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("city", "Rio de Janeiro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Rock in Rio 2026 - Dia 1"))
                .andExpect(jsonPath("$.content[0].priceFrom.amount").value(650.00))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /events with no filters at all still works")
    void listsCatalogueWithoutFilters() throws Exception {
        // Regression test. The first version of the query used `:city is null or
        // ...`, which PostgreSQL rejected with "function lower(bytea) does not
        // exist" whenever city was absent - and every other test passed a city,
        // so nothing caught it until the service was called for real.
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /events filtered by status only")
    void listsCatalogueByStatusOnly() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("status", "ON_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/v1/events").param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /events/{id} returns the categories with current availability")
    void showsEventDetail() throws Exception {
        mockMvc.perform(get("/api/v1/events/{id}", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].name").value("Pista"))
                .andExpect(jsonPath("$.categories[0].availableQuantity").value(100));
    }
}
