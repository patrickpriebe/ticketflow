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
    /** No answer within the deadline. Whether the charge happened is unknown. */
    TIMEOUT,
    /** The gateway answered with 5xx or something unparseable. */
    ERROR;

    /** Timeouts and errors leave the payment retryable; a decline does not. */
    public boolean isRetryable() {
        return this == TIMEOUT || this == ERROR;
    }
}
