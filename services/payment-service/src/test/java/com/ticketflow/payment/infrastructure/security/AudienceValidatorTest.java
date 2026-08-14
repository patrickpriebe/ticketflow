package com.ticketflow.payment.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O que separa "o Google emitiu este token" de "o Google emitiu este token para
 * nós".
 */
class AudienceValidatorTest {

    private static final String OUR_CLIENT = "123456789-ticketflow.apps.googleusercontent.com";

    private final AudienceValidator validator = new AudienceValidator(OUR_CLIENT);

    private static Jwt tokenFor(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("nao-importa")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .subject("110169484474386276334")
                .claim("email", "ana.souza@example.com")
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
        var result = validator.validate(tokenFor(List.of("999-outro-site.apps.googleusercontent.com")));

        assertThat(result.hasErrors())
                .as("""
                        Este é o token perigoso: assinatura legítima do Google, emissor
                        correto, e o mesmo `sub` que a pessoa tem aqui. Aceitá-lo deixa
                        quem controla qualquer site com "entrar com Google" comprar no
                        nome de quem entrar lá.""")
                .isTrue();
    }

    @Test
    @DisplayName("recusa token sem audience nenhum")
    void rejectsMissingAudience() {
        assertThat(validator.validate(tokenFor(List.of())).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("aceita quando o nosso audience esta entre varios")
    void acceptsWhenPresentAmongOthers() {
        assertThat(validator.validate(tokenFor(List.of("outro", OUR_CLIENT))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("nao existe validador sem audience configurado")
    void refusesToBeBuiltWithoutAudience() {
        assertThatThrownBy(() -> new AudienceValidator("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
