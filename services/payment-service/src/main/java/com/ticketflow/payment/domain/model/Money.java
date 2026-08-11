package com.ticketflow.payment.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A monetary amount with its currency.
 *
 * <p>Yes, this is nearly identical to the Order Service's {@code Money}. That
 * duplication is the deliberate price of having no shared module: neither service
 * can be forced to redeploy because the other changed a value object.
 */
public record Money(BigDecimal amount, String currency) {

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
        // Normalised so 10.0 and 10.00 compare equal - BigDecimal#equals is
        // scale-sensitive and would otherwise surprise every test.
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    @Override
    public String toString() {
        return currency + " " + amount.toPlainString();
    }
}
