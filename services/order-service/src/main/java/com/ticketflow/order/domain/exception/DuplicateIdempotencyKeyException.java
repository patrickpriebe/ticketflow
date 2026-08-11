package com.ticketflow.order.domain.exception;

/**
 * Raised by the persistence adapter when the unique constraint on
 * {@code orders.idempotency_key} rejects an insert.
 *
 * <p>This is not an error the caller ever sees: it means a concurrent request with
 * the same key won the race, and the use case answers by returning that order. The
 * constraint - not a SELECT beforehand - is what actually makes retries safe, since
 * check-then-insert has a window between the two.
 */
public class DuplicateIdempotencyKeyException extends DomainException {

    public DuplicateIdempotencyKeyException(String idempotencyKey) {
        super("duplicate-idempotency-key",
                "An order with idempotency key '%s' already exists.".formatted(idempotencyKey));
    }
}
