package com.ticketflow.order.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * The buyer.
 *
 * <p>Today the identity arrives in the request body, which is only acceptable
 * because the API has no authentication yet. Once auth exists, {@code id} must come
 * from the token - trusting a caller-supplied customer id would let anyone place
 * orders as anyone else. Tracked as a known debt in the README.
 */
public record Customer(UUID id, String name, String email) {

    public Customer {
        Objects.requireNonNull(id, "customer id is required");
        Objects.requireNonNull(name, "customer name is required");
        Objects.requireNonNull(email, "customer email is required");

        name = name.trim();
        email = email.trim().toLowerCase();

        if (name.length() < 2) {
            throw new IllegalArgumentException("customer name is too short: " + name);
        }
        // Deliberately shallow: real address validation is delivery, not a regex.
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw new IllegalArgumentException("customer email is not a valid address: " + email);
        }
    }
}
