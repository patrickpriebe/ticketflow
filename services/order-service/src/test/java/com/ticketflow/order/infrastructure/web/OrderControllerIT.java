package com.ticketflow.order.infrastructure.web;

import com.ticketflow.order.support.OrderServiceIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * O contrato HTTP de ponta a ponta, com PostgreSQL de verdade. O Flyway aplica as
 * mesmas migrations que a produção vai rodar, então divergência entre entidade e
 * schema falha aqui e não no deploy.
 *
 * <p>O relay fica desligado para que a linha do outbox possa ser observada no estado
 * em que é escrita. Publicação é assunto do {@code OrderFlowIT}.
 */
@SpringBootTest(properties = {
        "ticketflow.outbox.scheduling-enabled=false",
        "ticketflow.auth.secret=" + OrderServiceIT.TEST_SECRET,
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

    private MvcResult placeOrder(UUID categoryId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(categoryId, quantity)))
                .andReturn();
    }

    @Test
    @DisplayName("POST /orders é aceito na hora como PENDING, com Location para acompanhar")
    void acceptsOrder() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(PISTA_ID, 2)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount.amount").value(1300.00))
                .andExpect(jsonPath("$.items[0].categoryName").value("Pista"))
                // A identidade veio do token, não do corpo - o corpo nem tem esse campo.
                .andExpect(jsonPath("$.customer.id").value(CUSTOMER_ID.toString()))
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("PENDING"));
    }

    @Test
    @DisplayName("o pedido e seu ORDER_CREATED são escritos na mesma transação")
    void writesOutboxAtomically() throws Exception {
        MvcResult result = placeOrder(PISTA_ID, 2);
        assertThat(result.getResponse().getStatus()).isEqualTo(202);

        UUID orderId = UUID.fromString(objectMapper
                .readTree(result.getResponse().getContentAsString()).get("id").asText());

        Map<String, Object> outbox = jdbc.queryForMap(
                "SELECT event_type, topic, partition_key, status FROM outbox_messages WHERE aggregate_id = ?",
                orderId);

        assertThat(outbox.get("event_type")).isEqualTo("ORDER_CREATED");
        assertThat(outbox.get("topic")).isEqualTo("ticketflow.orders.created");
        assertThat(outbox.get("partition_key")).isEqualTo(orderId.toString());
        assertThat(outbox.get("status")).isEqualTo("PENDING");

        assertThat(jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("repetir a Idempotency-Key devolve o pedido original, não um segundo")
    void replayIsIdempotent() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = orderBody(PISTA_ID, 2);

        MvcResult first = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", key)
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT reserved_quantity FROM ticket_categories WHERE id = ?", Integer.class, PISTA_ID))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("vender mais do que existe é recusado com 409 e corpo RFC 7807")
    void refusesToOversell() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(CAMAROTE_ID, 4)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ticketflow.dev/problems/insufficient-inventory"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    @DisplayName("sem Idempotency-Key é 400")
    void requiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(PISTA_ID, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://ticketflow.dev/problems/missing-header"));
    }

    @Nested
    @DisplayName("autenticação")
    class Authentication {

        @Test
        @DisplayName("comprar sem token é 401")
        void anonymousCannotBuy() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(orderBody(PISTA_ID, 1)))
                    .andExpect(status().isUnauthorized());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        }

        @Test
        @DisplayName("consultar pedido sem token é 401")
        void anonymousCannotRead() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("token assinado com outro segredo é 401")
        void forgedTokenIsRejected() throws Exception {
            // Um token que "parece" válido mas foi assinado por outra pessoa.
            String forged = "Bearer eyJhbGciOiJIUzI1NiJ9."
                    + "eyJzdWIiOiIzZjFjOWE2ZS03N2IyLTRjMGQtOWYzMS0yYTViOGU0ZDZjMTAifQ.assinatura-invalida";

            mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID())
                            .header("Authorization", forged))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("um cliente não enxerga o pedido de outro")
        void cannotReadAnotherCustomersOrder() throws Exception {
            MvcResult mine = placeOrder(PISTA_ID, 1);
            String orderId = objectMapper
                    .readTree(mine.getResponse().getContentAsString()).get("id").asText();

            // Mesmo pedido, token de outra pessoa.
            mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                            .header("Authorization", bearerToken(UUID.randomUUID())))
                    // 404 e não 403: um 403 confirmaria que esse id existe, o que já
                    // é informação demais para quem está sondando.
                    .andExpect(status().isNotFound());

            // E o dono continua enxergando normalmente.
            mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                            .header("Authorization", bearerToken()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a listagem devolve só os pedidos de quem chamou")
        void listsOnlyOwnOrders() throws Exception {
            placeOrder(PISTA_ID, 1);

            mockMvc.perform(get("/api/v1/orders").header("Authorization", bearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.totalElements").value(1));

            // Não existe parâmetro de cliente para trocar: a lista é sempre a de quem
            // apresentou o token.
            mockMvc.perform(get("/api/v1/orders").header("Authorization", bearerToken(UUID.randomUUID())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        @DisplayName("o catálogo continua público")
        void catalogueStaysPublic() throws Exception {
            mockMvc.perform(get("/api/v1/events"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.totalElements").value(1));
        }
    }

    @Test
    @DisplayName("pedido inexistente é 404 com corpo de problema")
    void unknownOrderIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID())
                        .header("Authorization", bearerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://ticketflow.dev/problems/order-not-found"));
    }

    @Test
    @DisplayName("GET /events lista o catálogo com o menor preço")
    void listsCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("city", "Rio de Janeiro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Rock in Rio 2026 - Dia 1"))
                .andExpect(jsonPath("$.content[0].priceFrom.amount").value(650.00));
    }

    @Test
    @DisplayName("GET /events sem filtro nenhum funciona")
    void listsCatalogueWithoutFilters() throws Exception {
        // Regressão: a primeira versão da query usava `:city is null or ...`, que o
        // PostgreSQL rejeitava com "function lower(bytea) does not exist".
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /events/{id} traz as categorias com disponibilidade")
    void showsEventDetail() throws Exception {
        mockMvc.perform(get("/api/v1/events/{id}", EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].name").value("Pista"))
                .andExpect(jsonPath("$.categories[0].availableQuantity").value(100));
    }
}
