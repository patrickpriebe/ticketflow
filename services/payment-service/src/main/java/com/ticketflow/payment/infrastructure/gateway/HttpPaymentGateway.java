package com.ticketflow.payment.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.payment.application.port.out.PaymentGateway;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Talks to the external payment provider over HTTP.
 *
 * <p>The single place in TicketFlow allowed to make an outbound HTTP call. It never
 * throws for a business outcome: a decline, a timeout and a 5xx all come back as
 * responses, because the domain has a different opinion about each of them and
 * exceptions would flatten that into "it broke".
 */
public class HttpPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpPaymentGateway.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry registry;

    public HttpPaymentGateway(RestClient restClient, ObjectMapper objectMapper, MeterRegistry registry) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    @Override
    public AuthorizationResponse authorize(AuthorizationRequest request) {
        long startedAt = System.nanoTime();
        try {
            return record(doAuthorize(request, startedAt), startedAt);
        } catch (RuntimeException e) {
            // Should not happen - doAuthorize converts failures into responses - but
            // a metric that lies is worse than no metric.
            record(AuthorizationResponse.errored(rootMessage(e), null, elapsedMs(startedAt), null), startedAt);
            throw e;
        }
    }

    /**
     * Estorno no gateway simulado.
     *
     * <p>Mesma leitura de status da autorização, com uma diferença que importa:
     * {@code 4xx} aqui é <strong>recusa definitiva</strong> e {@code 5xx} ou
     * timeout é <strong>indisponibilidade</strong>. Confundir os dois custa caro
     * nas duas direções — tratar recusa como indisponível tenta devolver o
     * dinheiro para sempre, e tratar indisponível como recusa deixa o cliente
     * sem o dinheiro dele.
     */
    @Override
    public RefundResponse refund(RefundRequest request) {
        long startedAt = System.nanoTime();
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri("/refunds")
                    .contentType(MediaType.APPLICATION_JSON)
                    // A mesma chave da cobrança: uma reentrega do ORDER_CANCELLED
                    // não pode virar dois estornos.
                    .header("Idempotency-Key", "refund-" + request.idempotencyKey())
                    .body(Map.of(
                            "transactionId", request.transactionId(),
                            "amount", request.amount(),
                            "currency", request.currency()))
                    .retrieve()
                    .onStatus(status -> true, (req, res) -> {
                    })
                    .toEntity(String.class);

            int latency = elapsedMs(startedAt);
            HttpStatusCode status = response.getStatusCode();

            if (status.is2xxSuccessful()) {
                JsonNode body = parse(response.getBody());
                String refundId = text(body, "refundId");
                if (refundId == null || refundId.isBlank()) {
                    // Sem comprovante não dá para afirmar que devolveu.
                    return RefundResponse.unavailable(
                            "provedor respondeu sem refundId", status.value());
                }
                recordRefund("REFUNDED", startedAt);
                return RefundResponse.refunded(refundId, status.value());
            }

            if (status.is4xxClientError()) {
                recordRefund("DECLINED", startedAt);
                return RefundResponse.declined(
                        "provedor recusou o estorno: " + response.getBody(), status.value());
            }

            recordRefund("UNAVAILABLE", startedAt);
            return RefundResponse.unavailable("provedor indisponivel", status.value());

        } catch (RuntimeException e) {
            log.warn("Estorno falhou para o pagamento {}", request.paymentId(), e);
            recordRefund("UNAVAILABLE", startedAt);
            return RefundResponse.unavailable(rootMessage(e), null);
        }
    }

    private void recordRefund(String outcome, long startedAt) {
        Timer.builder("ticketflow.payment.gateway.refunds")
                .description("Refund calls to the external payment provider")
                .tag("outcome", outcome)
                .register(registry)
                .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
    }

    /**
     * One timer, tagged by outcome. Approval rate, decline rate and how long the
     * provider takes all come from the same series - and a rise in TIMEOUT is
     * visible before customers start complaining.
     */
    private AuthorizationResponse record(AuthorizationResponse response, long startedAt) {
        Timer.builder("ticketflow.payment.gateway.calls")
                .description("Calls to the external payment provider")
                .tag("outcome", response.outcome().name())
                .register(registry)
                .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        return response;
    }

    private AuthorizationResponse doAuthorize(AuthorizationRequest request, long startedAt) {
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(request.path())
                    .contentType(MediaType.APPLICATION_JSON)
                    // Makes a retry safe: if the provider already processed this
                    // charge, it returns the original instead of creating a second.
                    .header("Idempotency-Key", request.idempotencyKey())
                    .body(request.body())
                    .retrieve()
                    // Status is interpreted below, not turned into an exception.
                    .onStatus(status -> true, (req, res) -> {
                    })
                    .toEntity(String.class);

            return interpret(response, elapsedMs(startedAt));

        } catch (RuntimeException e) {
            // One catch on purpose. RestClient does not always wrap IO failures in
            // ResourceAccessException, so keying off the exception type alone missed
            // real timeouts and reported them as generic errors - which would have
            // let a boleto be retried when it must not be. What matters is the root
            // cause, not the wrapper.
            int latency = elapsedMs(startedAt);

            if (isTimeout(e)) {
                // Not a decline: nobody knows whether the money moved.
                log.warn("Gateway timed out after {}ms for payment {}", latency, request.paymentId());
                return AuthorizationResponse.timedOut(rootMessage(e), latency);
            }

            log.warn("Gateway call failed for payment {}", request.paymentId(), e);
            return AuthorizationResponse.errored(rootMessage(e), null, latency, null);
        }
    }

    private AuthorizationResponse interpret(ResponseEntity<String> response, int latencyMs) {
        HttpStatusCode status = response.getStatusCode();
        String raw = response.getBody();

        if (status.is5xxServerError()) {
            return AuthorizationResponse.errored("HTTP " + status.value(), status.value(), latencyMs, raw);
        }

        JsonNode body = parse(raw);
        if (body == null) {
            return AuthorizationResponse.errored(
                    "Unreadable gateway response", status.value(), latencyMs, raw);
        }

        String outcome = text(body, "status");

        if (status.is2xxSuccessful() && "approved".equalsIgnoreCase(outcome)) {
            String transactionId = text(body, "transactionId");
            if (transactionId == null || transactionId.isBlank()) {
                // Approved without an id is unusable - we could never reconcile or refund it.
                return AuthorizationResponse.errored(
                        "Gateway approved without a transactionId", status.value(), latencyMs, raw);
            }
            return AuthorizationResponse.approved(transactionId, status.value(), latencyMs, raw);
        }

        if ("declined".equalsIgnoreCase(outcome) || status.value() == 402) {
            String code = text(body, "code");
            return AuthorizationResponse.rejected(
                    code == null ? "DECLINED" : code,
                    text(body, "message"),
                    status.value(), latencyMs, raw);
        }

        // A 4xx that is not a decline means we sent something wrong. Retrying an
        // identical bad request would not help, but it is not a customer decline
        // either - surface it as an error so it shows up in the logs and metrics.
        return AuthorizationResponse.errored(
                "Unexpected gateway response: HTTP " + status.value(), status.value(), latencyMs, raw);
    }

    private JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            // InterruptedIOException covers SocketTimeoutException and its friends
            // across the different HTTP client implementations.
            if (cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof InterruptedIOException) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static int elapsedMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
