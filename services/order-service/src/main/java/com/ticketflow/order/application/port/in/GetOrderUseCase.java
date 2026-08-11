package com.ticketflow.order.application.port.in;

import com.ticketflow.order.domain.model.Order;

import java.util.UUID;

/**
 * Driving port: read one order.
 *
 * <p>This is what a client polls after receiving 202, and what the phase-4 front-end
 * renders as the order timeline.
 */
public interface GetOrderUseCase {

    /**
     * @throws com.ticketflow.order.domain.exception.OrderNotFoundException if absent
     */
    Order execute(UUID orderId);
}
