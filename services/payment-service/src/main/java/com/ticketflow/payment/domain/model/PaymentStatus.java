package com.ticketflow.payment.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Outcome of a payment attempt cycle.
 *
 * <p>{@link #REJECTED} and {@link #FAILED} are not the same thing and collapsing
 * them would destroy information:
 *
 * <ul>
 *   <li>{@code REJECTED} - the gateway answered "no" (no funds, expired card). It is
 *       a final answer and the customer has to be told.</li>
 *   <li>{@code FAILED} - the gateway never gave a usable answer (timeout, 5xx).
 *       Nobody knows whether the money moved, and a retry may still succeed.</li>
 * </ul>
 */
public enum PaymentStatus {

    PENDING,
    APPROVED,
    REJECTED,
    FAILED;

    private static final Set<PaymentStatus> FROM_PENDING =
            Collections.unmodifiableSet(EnumSet.of(APPROVED, REJECTED, FAILED));

    public boolean canTransitionTo(PaymentStatus target) {
        if (target == null || target == this) {
            return false;
        }
        // A FAILED payment may still be retried into a final answer. APPROVED and
        // REJECTED are terminal.
        if (this == FAILED) {
            return target == APPROVED || target == REJECTED;
        }
        return this == PENDING && FROM_PENDING.contains(target);
    }

    public boolean isFinal() {
        return this == APPROVED || this == REJECTED;
    }
}
