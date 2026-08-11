package com.ticketflow.order.domain.exception;

import java.util.UUID;

public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException(UUID orderId) {
        super("order-not-found", "Order %s does not exist.".formatted(orderId));
    }
}
