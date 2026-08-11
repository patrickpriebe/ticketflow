package com.ticketflow.order.application.port.out;

import java.util.function.Supplier;

/**
 * Driven port: runs a block of work as one atomic transaction.
 *
 * <p>This exists so a use case can demand atomicity without importing
 * {@code @Transactional}. The order insert and the outbox insert must commit or roll
 * back together; the Spring implementation of that lives in the infrastructure
 * layer, where framework annotations belong.
 *
 * <p>In unit tests the fake implementation simply runs the supplier, which keeps the
 * use case testable with no Spring context at all.
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
