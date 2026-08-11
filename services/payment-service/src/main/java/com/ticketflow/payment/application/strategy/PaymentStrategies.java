package com.ticketflow.payment.application.strategy;

import com.ticketflow.payment.domain.model.PaymentMethod;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the strategy for a payment method.
 *
 * <p>Validates its own completeness at construction: if someone adds a value to
 * {@link PaymentMethod} and forgets the strategy, the application refuses to start
 * instead of failing later on a real customer's order. A missing branch in an
 * if/else would simply have fallen through.
 */
public class PaymentStrategies {

    private final Map<PaymentMethod, PaymentStrategy> byMethod = new EnumMap<>(PaymentMethod.class);

    public PaymentStrategies(List<PaymentStrategy> strategies) {
        Objects.requireNonNull(strategies, "strategies are required");

        for (PaymentStrategy strategy : strategies) {
            PaymentStrategy previous = byMethod.put(strategy.method(), strategy);
            if (previous != null) {
                throw new IllegalStateException(
                        "Two strategies claim %s: %s and %s".formatted(
                                strategy.method(),
                                previous.getClass().getSimpleName(),
                                strategy.getClass().getSimpleName()));
            }
        }

        List<PaymentMethod> uncovered = java.util.Arrays.stream(PaymentMethod.values())
                .filter(method -> !byMethod.containsKey(method))
                .toList();
        if (!uncovered.isEmpty()) {
            throw new IllegalStateException("No PaymentStrategy registered for: " + uncovered);
        }
    }

    public PaymentStrategy forMethod(PaymentMethod method) {
        PaymentStrategy strategy = byMethod.get(method);
        if (strategy == null) {
            throw new IllegalStateException("No PaymentStrategy registered for " + method);
        }
        return strategy;
    }
}
