package com.ticketflow.payment.application.port.out;

import com.ticketflow.payment.domain.model.AttemptOutcome;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Driven port: the external payment provider.
 *
 * <p>The only outbound HTTP in the whole system lives behind this port. A call from
 * here to another TicketFlow service would break the architecture - the three
 * services only ever talk through Kafka.
 *
 * <p>The port never throws for a business outcome. A decline, a timeout and a 5xx
 * are all just {@link AuthorizationResponse}s with different outcomes, because all
 * three are things the domain has an opinion about.
 */
public interface PaymentGateway {

    AuthorizationResponse authorize(AuthorizationRequest request);

    /**
     * @param idempotencyKey sent to the provider so a retried call cannot charge the
     *                       customer twice. It is the payment id, which is stable
     *                       across retries of the same payment.
     * @param path           endpoint chosen by the strategy for this method
     * @param body           method-specific payload built by the strategy. Must never
     *                       contain a full card number, CVV or expiry date.
     */
    record AuthorizationRequest(UUID paymentId,
                                String idempotencyKey,
                                String path,
                                Map<String, Object> body) {

        public AuthorizationRequest {
            Objects.requireNonNull(paymentId, "paymentId is required");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
            Objects.requireNonNull(path, "path is required");
            body = Map.copyOf(Objects.requireNonNull(body, "body is required"));
        }
    }

    record AuthorizationResponse(AttemptOutcome outcome,
                                 String transactionId,
                                 String failureCode,
                                 String failureReason,
                                 Integer httpStatus,
                                 Integer latencyMs,
                                 String rawResponse) {

        public AuthorizationResponse {
            Objects.requireNonNull(outcome, "outcome is required");
        }

        public static AuthorizationResponse approved(String transactionId, int httpStatus,
                                                     int latencyMs, String raw) {
            return new AuthorizationResponse(AttemptOutcome.APPROVED, transactionId,
                    null, null, httpStatus, latencyMs, raw);
        }

        /**
         * The provider took the charge; the answer comes later, by webhook.
         *
         * <p>The transaction id is mandatory here even though nothing was decided:
         * it is the only way the webhook will find this payment again.
         */
        public static AuthorizationResponse accepted(String transactionId, int httpStatus,
                                                     int latencyMs, String raw) {
            return new AuthorizationResponse(AttemptOutcome.ACCEPTED, transactionId,
                    null, null, httpStatus, latencyMs, raw);
        }

        public static AuthorizationResponse rejected(String failureCode, String failureReason,
                                                     int httpStatus, int latencyMs, String raw) {
            return new AuthorizationResponse(AttemptOutcome.REJECTED, null,
                    failureCode, failureReason, httpStatus, latencyMs, raw);
        }

        public static AuthorizationResponse timedOut(String detail, int latencyMs) {
            return new AuthorizationResponse(AttemptOutcome.TIMEOUT, null,
                    "GATEWAY_TIMEOUT", detail, null, latencyMs, null);
        }

        public static AuthorizationResponse errored(String detail, Integer httpStatus,
                                                    int latencyMs, String raw) {
            return new AuthorizationResponse(AttemptOutcome.ERROR, null,
                    "GATEWAY_ERROR", detail, httpStatus, latencyMs, raw);
        }
    }
}
