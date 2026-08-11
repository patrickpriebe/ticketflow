package com.ticketflow.payment.domain.model;

import java.util.Objects;

/**
 * One recorded call to the external gateway.
 *
 * <p>This is what makes the Wiremock scenarios observable: a timeout test must be
 * able to prove an attempt was stored with {@link AttemptOutcome#TIMEOUT}, not just
 * that a method threw.
 *
 * @param requestPayload masked request. Card number, CVV and expiry must never
 *                       appear here - only the brand and the last four digits.
 */
public record PaymentAttempt(int attemptNumber,
                             AttemptOutcome outcome,
                             Integer httpStatus,
                             Integer latencyMs,
                             String requestPayload,
                             String responsePayload,
                             String errorMessage) {

    public PaymentAttempt {
        Objects.requireNonNull(outcome, "outcome is required");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be positive, got: " + attemptNumber);
        }
    }
}
