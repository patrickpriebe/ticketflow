package com.ticketflow.order.domain.model;

/**
 * How the customer intends to pay.
 *
 * <p>Carried in the ORDER_CREATED event; on the Payment Service side this value
 * selects a {@code PaymentStrategy} implementation. Adding a method there must mean
 * adding a strategy, never another branch in an if/else.
 */
public enum PaymentMethod {
    CREDIT_CARD,
    PIX,
    BOLETO
}
