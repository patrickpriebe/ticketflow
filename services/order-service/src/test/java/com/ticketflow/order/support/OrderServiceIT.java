package com.ticketflow.order.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared plumbing for the integration tests: a real PostgreSQL, a clean schema and a
 * known catalogue before every test.
 */
public abstract class OrderServiceIT {

    /**
     * Started only when no external database is supplied - see
     * {@link #datasource(DynamicPropertyRegistry)}.
     */
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    protected static final UUID EVENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    protected static final UUID PISTA_ID = UUID.fromString("aaaaaaaa-0001-4000-8000-000000000001");
    protected static final UUID CAMAROTE_ID = UUID.fromString("aaaaaaaa-0001-4000-8000-000000000003");
    protected static final UUID CUSTOMER_ID = UUID.fromString("3f1c9a6e-77b2-4c0d-9f31-2a5b8e4d6c10");

    /** Precisa bater com o `ticketflow.auth.secret` que os testes configuram. */
    public static final String TEST_SECRET = "segredo-de-teste-com-mais-de-32-caracteres";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * By default the tests start their own PostgreSQL through Testcontainers, which
     * is what CI does.
     *
     * <p>Passing {@code -Dticketflow.it.datasource.url=...} points them at a database
     * that is already running instead. That escape hatch exists because some Docker
     * Desktop builds on Windows do not expose a usable Engine API to JVM clients,
     * even though the docker CLI works fine - and a developer who cannot run the
     * integration tests will simply stop running them.
     *
     * <p>The target database is wiped before every test, so never point this at a
     * database whose contents matter.
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String externalUrl = System.getProperty("ticketflow.it.datasource.url");

        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username",
                    () -> System.getProperty("ticketflow.it.datasource.username", "ticketflow"));
            registry.add("spring.datasource.password",
                    () -> System.getProperty("ticketflow.it.datasource.password", "ticketflow"));
            return;
        }

        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void resetDatabase() {
        jdbc.update("DELETE FROM processed_events");
        jdbc.update("DELETE FROM outbox_messages");
        jdbc.update("DELETE FROM order_status_history");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM ticket_categories");
        jdbc.update("DELETE FROM events");

        // A sales window wide enough that these tests do not start failing on a
        // future date - time-dependent fixtures are a classic flaky test.
        Instant now = Instant.now();
        jdbc.update("""
                        INSERT INTO events (id, name, venue, city, starts_at, sales_start_at, sales_end_at, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'ON_SALE')
                        """,
                EVENT_ID, "Rock in Rio 2026 - Dia 1", "Parque Olimpico", "Rio de Janeiro",
                Timestamp.from(now.plus(365, ChronoUnit.DAYS)),
                Timestamp.from(now.minus(30, ChronoUnit.DAYS)),
                Timestamp.from(now.plus(364, ChronoUnit.DAYS)));

        jdbc.update("""
                        INSERT INTO ticket_categories (id, event_id, name, price_amount, currency, total_quantity)
                        VALUES (?, ?, 'Pista', 650.00, 'BRL', 100)
                        """,
                PISTA_ID, EVENT_ID);
        jdbc.update("""
                        INSERT INTO ticket_categories (id, event_id, name, price_amount, currency, total_quantity)
                        VALUES (?, ?, 'Camarote', 2400.00, 'BRL', 2)
                        """,
                CAMAROTE_ID, EVENT_ID);
    }

    /** O corpo não carrega quem compra: essa informação vem do token. */
    protected String orderBody(UUID categoryId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "paymentMethod", "CREDIT_CARD",
                "items", List.of(Map.of(
                        "ticketCategoryId", categoryId.toString(),
                        "quantity", quantity))));
    }

    /** Token do cliente padrão dos testes. */
    protected String bearerToken() {
        return bearerToken(CUSTOMER_ID);
    }

    /**
     * Assina um JWT com o mesmo segredo que o serviço valida.
     *
     * <p>Emitido aqui em vez de chamar o endpoint de desenvolvimento: um teste que
     * depende de outro endpoint estar ligado testa duas coisas ao mesmo tempo.
     */
    protected String bearerToken(UUID customerId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(customerId.toString())
                    .claim("name", "Ana Souza")
                    .claim("email", "ana.souza@example.com")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
            return "Bearer " + jwt.serialize();

        } catch (Exception e) {
            throw new IllegalStateException("não consegui assinar o token de teste", e);
        }
    }
}
