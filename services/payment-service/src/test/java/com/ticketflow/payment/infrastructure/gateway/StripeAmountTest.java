package com.ticketflow.payment.infrastructure.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Conversão de reais para centavos.
 *
 * <p>Teste pequeno e desproporcionalmente importante: o Stripe cobra em unidade
 * menor (centavos, inteiro), o domínio guarda {@code BigDecimal} com duas casas,
 * e errar a conversão não quebra nada — apenas cobra cem vezes mais ou cem vezes
 * menos. É o tipo de defeito que passa em revisão de código e aparece no extrato.
 */
class StripeAmountTest {

    @Test
    @DisplayName("reais viram centavos")
    void convertsToMinorUnits() {
        assertThat(StripePaymentGateway.toMinorUnits(new BigDecimal("650.00"))).isEqualTo(65_000L);
        assertThat(StripePaymentGateway.toMinorUnits(new BigDecimal("1.00"))).isEqualTo(100L);
        assertThat(StripePaymentGateway.toMinorUnits(new BigDecimal("0.50"))).isEqualTo(50L);
        assertThat(StripePaymentGateway.toMinorUnits(new BigDecimal("3700.00"))).isEqualTo(370_000L);
    }

    @Test
    @DisplayName("valor sem casas decimais tambem converte")
    void handlesWholeNumbers() {
        assertThat(StripePaymentGateway.toMinorUnits(new BigDecimal("90"))).isEqualTo(9_000L);
    }

    @Test
    @DisplayName("texto vindo do mapa da estrategia converte igual")
    void acceptsStringAmounts() {
        assertThat(StripePaymentGateway.toMinorUnits("2400.00")).isEqualTo(240_000L);
    }

    @Test
    @DisplayName("fracao de centavo derruba em vez de arredondar em silencio")
    void refusesSubCentPrecision() {
        // Arredondar aqui seria cobrar alguem a mais ou a menos sem ninguem saber.
        // Melhor falhar alto: se chegou fracao de centavo, algo esta errado antes.
        assertThatThrownBy(() -> StripePaymentGateway.toMinorUnits(new BigDecimal("10.005")))
                .isInstanceOf(ArithmeticException.class);
    }
}
