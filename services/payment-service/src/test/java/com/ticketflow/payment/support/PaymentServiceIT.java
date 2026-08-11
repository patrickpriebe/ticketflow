package com.ticketflow.payment.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Shared plumbing for the Payment Service integration tests: a real PostgreSQL, a
 * clean schema and a stubbed gateway the test can steer per scenario.
 */
public abstract class PaymentServiceIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** In-process, so these tests need no Docker for the gateway side. */
    protected static final WireMockServer GATEWAY = new WireMockServer(
            options().dynamicPort().globalTemplating(true));

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Testcontainers by default - what CI uses. Passing
     * {@code -Dticketflow.it.datasource.url=...} points the tests at a database that
     * is already running instead, which is how they run on a machine whose Docker
     * does not expose an Engine API to JVM clients.
     *
     * <p>The target database is wiped before every test. Never point it at one whose
     * contents matter.
     */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        if (!GATEWAY.isRunning()) {
            GATEWAY.start();
        }
        registry.add("ticketflow.gateway.base-url", GATEWAY::baseUrl);

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

    @AfterAll
    static void stopGateway() {
        GATEWAY.stop();
    }

    @BeforeEach
    void resetState() {
        GATEWAY.resetAll();
        jdbc.update("DELETE FROM payment_attempts");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM processed_events");
        jdbc.update("DELETE FROM outbox_messages");
    }
}
