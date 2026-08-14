package com.ticketflow.notification.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A mesma trava do Order Service.
 *
 * <p>Existe nos dois lados porque um serviço aberto e o outro fechado é pior que
 * os dois abertos: o sistema parece seguro pela porta que alguém testou. Aqui a
 * porta dá para o ingresso, que é documento pessoal.
 */
class JwtDecoderSelectionTest {

    private static final String LONG_ENOUGH_SECRET = "segredo-de-teste-com-mais-de-32-caracteres";

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    @Test
    @DisplayName("sem provedor e sem segredo o boot cai")
    void refusesToStartWithNeither() {
        assertThatThrownBy(() -> configuration.jwtDecoder("", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer-uri");
    }

    @Test
    @DisplayName("provedor sem audience o boot cai")
    void refusesProviderWithoutAudience() {
        assertThatThrownBy(() ->
                configuration.jwtDecoder("https://accounts.google.com", "", LONG_ENOUGH_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audience");
    }

    @Test
    @DisplayName("segredo curto o boot cai")
    void refusesShortSecret() {
        assertThatThrownBy(() -> configuration.jwtDecoder("", "", "curto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("so o segredo local monta o decoder simetrico")
    void buildsSymmetricDecoderWhenOnlySecretIsSet() {
        assertThat(configuration.jwtDecoder("", "", LONG_ENOUGH_SECRET)).isNotNull();
    }
}
