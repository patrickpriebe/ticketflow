package com.ticketflow.order.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A monetary amount together with its currency.
 *
 * <p>{@code BigDecimal}, never {@code double}: {@code 0.1 + 0.2} in binary floating
 * point is not {@code 0.3}, and in money that is a defect, not a rounding detail.
 *
 * <p>The amount is normalised to two decimal places on construction. Without that,
 * {@code new BigDecimal("10.0")} and {@code new BigDecimal("10.00")} would be
 * unequal - {@code BigDecimal#equals} compares scale as well as value - and this
 * record's generated {@code equals} would surprise every test that uses it.
 */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {

    private static final int SCALE = 2;

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO code, got: " + currency);
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount cannot be negative: " + amount);
        }
        currency = currency.toUpperCase();
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("multiplier cannot be negative: " + multiplier);
        }
        return new Money(amount.multiply(BigDecimal.valueOf(multiplier)), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other is required");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "cannot combine amounts in different currencies: %s and %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }
}
