package com.ticketflow.notification.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O mesmo teste do Order Service, pela mesma razão de a classe ser duplicada:
 * os dois serviços validam token do mesmo emissor e a regra não pode divergir.
 * Aqui o que está em jogo é ler o ingresso de outra pessoa.
 */
class AudienceValidatorTest {

    private static final String OUR_CLIENT = "123456789-ticketflow.apps.googleusercontent.com";

    private final AudienceValidator validator = new AudienceValidator(OUR_CLIENT);

    private static Jwt tokenFor(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("nao-importa")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .subject("110169484474386276334")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        return audience.isEmpty() ? builder.build() : builder.audience(audience).build();
    }

    @Test
    @DisplayName("aceita o token emitido para esta aplicacao")
    void acceptsOurOwnAudience() {
        assertThat(validator.validate(tokenFor(List.of(OUR_CLIENT))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("recusa token emitido para outro aplicativo do mesmo provedor")
    void rejectsAnotherApplication() {
        assertThat(validator.validate(tokenFor(List.of("999-outro-site.apps.googleusercontent.com")))
                .hasErrors()).isTrue();
    }

    @Test
    @DisplayName("recusa token sem audience nenhum")
    void rejectsMissingAudience() {
        assertThat(validator.validate(tokenFor(List.of())).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("nao existe validador sem audience configurado")
    void refusesToBeBuiltWithoutAudience() {
        assertThatThrownBy(() -> new AudienceValidator(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
