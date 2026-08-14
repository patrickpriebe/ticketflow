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

    /**
     * A coluna {@code payment_attempts.response_payload} e do tipo {@code json}.
     * Gravar uma string solta ali derruba o INSERT com "invalid input syntax for
     * type json" — e o erro aponta para o banco quando a causa esta no adaptador.
     *
     * <p>Foi exatamente assim que quebrou na primeira compra real: o pedido ficou
     * PENDING para sempre, sem nada na tela indicando o motivo.
     */
    @Test
    @DisplayName("o rastro gravado na tentativa e JSON valido")
    void traceIsValidJson() {
        String trace = StripePaymentGateway.trace("succeeded", "pi_3ABC123");

        assertThat(trace).isEqualTo("{\"status\":\"succeeded\",\"paymentIntent\":\"pi_3ABC123\"}");
    }

    @Test
    @DisplayName("aspas vindas do provedor nao quebram o JSON")
    void traceEscapesQuotes() {
        String trace = StripePaymentGateway.trace("esta\"ranho", "pi_1");

        assertThat(trace).isEqualTo("{\"status\":\"esta\\\"ranho\",\"paymentIntent\":\"pi_1\"}");
    }

    @Test
    @DisplayName("valor nulo nao vira a palavra null dentro do JSON")
    void traceHandlesNull() {
        assertThat(StripePaymentGateway.trace("erro", null))
                .isEqualTo("{\"status\":\"erro\",\"paymentIntent\":\"\"}");
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
