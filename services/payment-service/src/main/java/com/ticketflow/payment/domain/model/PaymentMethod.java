package com.ticketflow.payment.domain.model;

/**
 * How the customer chose to pay.
 *
 * <p>Each value has exactly one {@code PaymentStrategy}. Adding a method means
 * adding a strategy bean and a value here - never another branch in an if/else.
 */
public enum PaymentMethod {
    CREDIT_CARD,
    PIX,
    BOLETO
}
