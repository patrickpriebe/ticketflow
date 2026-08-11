package com.ticketflow.payment.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.ticketflow.payment.application.port.out.PaymentGateway.AuthorizationRequest;
import com.ticketflow.payment.application.port.out.PaymentGateway.AuthorizationResponse;
import com.ticketflow.payment.domain.model.AttemptOutcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway client against a stubbed provider.
 *
 * <p>Four scenarios, because a happy path alone does not count as covered: approval,
 * decline, timeout and 5xx. Wiremock is what makes the last two reproducible - you
 * cannot ask a real provider to time out on demand.
 *
 * <p>Named {@code *Test} rather than {@code *IT} deliberately: Wiremock runs
 * in-process and needs no Docker, so there is no reason to gate these behind
 * {@code verify}. They should run on every {@code mvn test}.
 */
class HttpPaymentGatewayTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);

    private static WireMockServer wiremock;
    private HttpPaymentGateway gateway;

    @BeforeAll
    static void startWiremock() {
        wiremock = new WireMockServer(options().dynamicPort());
        wiremock.start();
    }

    @AfterAll
    static void stopWiremock() {
        wiremock.stop();
    }

    @BeforeEach
    void setUp() {
        wiremock.resetAll();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(READ_TIMEOUT);

        RestClient restClient = RestClient.builder()
                .baseUrl(wiremock.baseUrl())
                .requestFactory(factory)
                .build();

        gateway = new HttpPaymentGateway(restClient, new ObjectMapper());
    }

    private AuthorizationRequest request() {
        UUID paymentId = UUID.randomUUID();
        return new AuthorizationRequest(paymentId, paymentId.toString(), "/v1/charges",
                Map.of("amount", "1300.00", "currency", "BRL", "method", "credit_card"));
    }

    @Test
    @DisplayName("an approved charge comes back APPROVED with the transaction id")
    void approved() {
        wiremock.stubFor(post(urlEqualTo("/v1/charges")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"approved\",\"transactionId\":\"ch_3PqR7s2eZvKYlo2C\"}")));

        AuthorizationResponse response = gateway.authorize(request());

        assertThat(response.outcome()).isEqualTo(AttemptOutcome.APPROVED);
        assertThat(response.transactionId()).isEqualTo("ch_3PqR7s2eZvKYlo2C");
        assertThat(response.httpStatus()).isEqualTo(201);
        assertThat(response.latencyMs()).isNotNull();
    }

    @Test
    @DisplayName("the idempotency key is sent on every call")
    void sendsIdempotencyKey() {
        wiremock.stubFor(post(urlEqualTo("/v1/charges")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"approved\",\"transactionId\":\"ch_1\"}")));

        AuthorizationRequest request = request();
        gateway.authorize(request);

        // Without this header a retry would charge the customer twice.
        wiremock.verify(postRequestedFor(urlEqualTo("/v1/charges"))
                .withHeader("Idempotency-Key", equalTo(request.idempotencyKey())));
    }

    @Test
    @DisplayName("a declined card comes back REJECTED with the issuer's reason")
    void declined() {
        wiremock.stubFor(post(urlEqualTo("/v1/charges")).willReturn(aResponse()
                .withStatus(402)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"declined\",\"code\":\"INSUFFICIENT_FUNDS\","
                        + "\"message\":\"Card declined by issuer\"}")));

        AuthorizationResponse response = gateway.authorize(request());

        assertThat(response.outcome()).isEqualTo(AttemptOutcome.REJECTED);
        assertThat(response.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(response.failureReason()).isEqualTo("Card declined by issuer");
        assertThat(response.transactionId()).isNull();
    }

    @Test
    @DisplayName("a provider that never answers comes back TIMEOUT, not REJECTED")
    void timesOut() {
        wiremock.stubFor(post(urlEqualTo("/v1/charges")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"approved\",\"transactionId\":\"ch_late\"}")
                // Longer than the read timeout, so the client gives up first.
                .withFixedDelay((int) READ_TIMEOUT.toMillis() * 4)));

        AuthorizationResponse response = gateway.authorize(request());

        // The distinction that matters: nobody knows whether the charge went through,
        // so this must never be reported to the customer as a decline.
        assertThat(response.outcome())
                .as("failureReason=%s", response.failureReason())
                .isEqualTo(AttemptOutcome.TIMEOUT);
        assertThat(response.outcome().isRetryable()).isTrue();
        assertThat(response.failureCode()).isEqualTo("GATEWAY_TIMEOUT");
        assertThat(response.transactionId()).isNull();
    }

    @Test
    @DisplayName("a 5xx comes back ERROR and is retryable")
    void serverError() {
        wiremock.stubFor(post(urlEqualTo("/v1/charges")).willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"upstream unavailable\"}")));

        AuthorizationResponse response = gateway.authorize(request());

        assertThat(response.outcome()).isEqualTo(AttemptOutcome.ERROR);
        assertThat(response.httpStatus()).isEqualTo(503);
        assertThat(response.outcome().isRetryable()).isTrue();
    }

    @Test
    @DisplayName("a connection that is refused outright is an ERROR, not a crash")
    void connectionRefused() {
        RestClient unreachable = RestClient.builder()
                // Nothing listens here.
                .baseUrl("http://localhost:1")
                .build();
        HttpPaymentGateway broken = new HttpPaymentGateway(unreachable, new ObjectMapper());

        AuthorizationResponse response = broken.authorize(request());

        assertThat(response.outcome()).isEqualTo(AttemptOutcome.ERROR);
    }

    @Test
    @DisplayName("an approval with no transaction id is treated as an error, not a success")
    void approvedWithoutTransactionId() {
        wiremock.stubFor(post(urlEqualTo("/v1/charges")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"approved\"}")));

        AuthorizationResponse response = gateway.authorize(request());

        // Accepting it would leave a charge we could never reconcile or refund.
        assertThat(response.outcome()).isEqualTo(AttemptOutcome.ERROR);
    }

    @Test
    @DisplayName("an unreadable body is an error rather than an accidental approval")
    void unreadableBody() {
        wiremock.stubFor(post(urlEqualTo("/v1/charges")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("not json at all")));

        AuthorizationResponse response = gateway.authorize(request());

        assertThat(response.outcome()).isEqualTo(AttemptOutcome.ERROR);
    }
}
