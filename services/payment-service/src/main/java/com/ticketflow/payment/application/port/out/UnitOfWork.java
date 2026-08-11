package com.ticketflow.payment.application.port.out;

import java.util.function.Supplier;

/**
 * Driven port: runs a block as one atomic transaction.
 *
 * <p>Exists so the use case can demand atomicity without importing
 * {@code @Transactional} - and, just as importantly, so it can be explicit about
 * what is <em>outside</em> a transaction. The gateway call is a network round trip
 * and must never be made while holding a database connection open.
 */
public interface UnitOfWork {

    <T> T execute(Supplier<T> work);

    default void executeVoid(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}
