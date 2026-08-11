package com.ticketflow.payment.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is paying, as far as the gateway needs to know.
 *
 * <p>Carries no card data on purpose. Payment credentials go from the customer
 * straight to the provider and never travel through Kafka, this service's database
 * or its logs.
 */
public record Payer(UUID customerId, String name, String email) {

    public Payer {
        Objects.requireNonNull(customerId, "customerId is required");
        Objects.requireNonNull(email, "email is required");
    }
}
