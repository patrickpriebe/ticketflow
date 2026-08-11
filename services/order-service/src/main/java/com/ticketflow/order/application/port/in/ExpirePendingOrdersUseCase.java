package com.ticketflow.order.application.port.in;

/**
 * Driving port: release orders whose payment never arrived.
 *
 * <p>Without this, a payment that fails leaves its tickets reserved forever. The
 * inventory is not lost to a bug but to silence - nobody ever says "this order is
 * dead" - and the event slowly sells out while seats sit held for customers who
 * never paid.
 */
public interface ExpirePendingOrdersUseCase {

    /**
     * @return how many orders were expired in this sweep
     */
    int execute();
}
