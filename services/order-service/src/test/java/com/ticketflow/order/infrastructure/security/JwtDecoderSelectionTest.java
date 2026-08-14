package com.ticketflow.order.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Trava a promessa da configuração de autenticação: <strong>não existe
 * combinação de variáveis que suba o serviço aceitando token sem verificar de
 * verdade.</strong>
 *
 * <p>Os três jeitos de errar cobertos aqui não quebram nenhum outro teste e não
 * aparecem em revisão — o serviço sobe, responde, e só está aberto. É a mesma
 * família de defeito que já apareceu neste projeto quando {@code dev-tokens}
 * valia {@code true} por padrão.
 *
 * <p>O caminho feliz do provedor não é testado aqui de propósito: montar aquele
 * decoder faz descoberta OIDC pela rede, e teste rápido não fala com a internet.
 * O que ele faz depois de montado é o que {@link AudienceValidatorTest} cobre.
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
                .as("""
                        Aceitar qualquer audience deixa entrar token legítimo do mesmo
                        provedor emitido para outro aplicativo. Falhar no boot é a única
                        resposta boa: o erro aparece no deploy, não em produção.""")
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
