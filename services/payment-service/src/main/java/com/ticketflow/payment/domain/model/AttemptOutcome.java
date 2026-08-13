package com.ticketflow.payment.domain.model;

/**
 * What happened on one call to the external gateway.
 *
 * <p>These four values are exactly the four scenarios the integration tests must
 * cover. A happy path alone does not count as covered.
 */
public enum AttemptOutcome {

    /** The gateway authorised the charge. */
    APPROVED,
    /** The gateway declined it - a real answer, just a negative one. */
    REJECTED,
    /**
     * The provider took the charge but has not decided yet; the answer arrives
     * later, by webhook.
     *
     * <p>This is not a failure and not a success, and collapsing it into either
     * would be wrong. A boleto is the honest example: the slip is issued now and
     * paid at a bank counter days later. Calling that APPROVED would hand out a
     * ticket nobody paid for; calling it FAILED would cancel an order that is
     * merely waiting.
     *
     * <p>The payment stays PENDING and keeps the provider's transaction id, which
     * is how the webhook finds it again.
     */
    ACCEPTED,
    /** No answer within the deadline. Whether the charge happened is unknown. */
    TIMEOUT,
    /** The gateway answered with 5xx or something unparseable. */
    ERROR;

    /** Timeouts and errors leave the payment retryable; a decline does not. */
    public boolean isRetryable() {
        return this == TIMEOUT || this == ERROR;
    }

    /** Whether this outcome ends the cycle, one way or the other. */
    public boolean isFinal() {
        return this == APPROVED || this == REJECTED;
    }
}
