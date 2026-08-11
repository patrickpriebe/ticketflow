package com.ticketflow.order.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("normalises scale so 10.0 and 10.00 are the same amount")
        void normalisesScale() {
            // Without setScale in the constructor this fails: BigDecimal#equals
            // compares scale too, and the record's generated equals would inherit that.
            assertThat(Money.of("10.0", "BRL")).isEqualTo(Money.of("10.00", "BRL"));
        }

        @Test
        @DisplayName("rounds half up to two decimals")
        void roundsHalfUp() {
            assertThat(Money.of("10.005", "BRL").amount()).isEqualByComparingTo(new BigDecimal("10.01"));
        }

        @Test
        @DisplayName("upper-cases the currency code")
        void upperCasesCurrency() {
            assertThat(Money.of("10.00", "brl").currency()).isEqualTo("BRL");
        }

        @Test
        @DisplayName("rejects a negative amount")
        void rejectsNegative() {
            assertThatThrownBy(() -> Money.of("-1.00", "BRL"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        @DisplayName("rejects a currency that is not a 3-letter code")
        void rejectsBadCurrency() {
            assertThatThrownBy(() -> Money.of("1.00", "REAL"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        void adds() {
            assertThat(Money.of("650.00", "BRL").plus(Money.of("100.50", "BRL")))
                    .isEqualTo(Money.of("750.50", "BRL"));
        }

        @Test
        void multiplies() {
            assertThat(Money.of("650.00", "BRL").times(2)).isEqualTo(Money.of("1300.00", "BRL"));
        }

        @Test
        @DisplayName("refuses to mix currencies instead of silently converting")
        void refusesMixedCurrencies() {
            assertThatThrownBy(() -> Money.of("10.00", "BRL").plus(Money.of("10.00", "USD")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different currencies");
        }

        @Test
        @DisplayName("multiplying by zero gives zero, not an error")
        void timesZero() {
            assertThat(Money.of("650.00", "BRL").times(0).isZero()).isTrue();
        }
    }
}
