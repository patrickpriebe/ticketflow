package com.ticketflow.order.domain.exception;

import com.ticketflow.order.domain.model.OrderStatus;

public class InvalidOrderStatusTransitionException extends DomainException {

    public InvalidOrderStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("invalid-order-transition",
                "An order cannot go from %s to %s.".formatted(from, to));
    }
}
