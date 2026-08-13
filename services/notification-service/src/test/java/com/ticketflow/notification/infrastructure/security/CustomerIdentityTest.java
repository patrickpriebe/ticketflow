package com.ticketflow.notification.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerIdentityTest {

    /**
     * Vetor idêntico ao do {@code AuthenticatedCustomerTest} no Order Service.
     *
     * <p>É de propósito que este literal apareça duas vezes no repositório: os dois
     * serviços derivam o id do cliente sozinhos, e o dia em que as regras
     * divergirem o ingresso simplesmente some da tela de quem comprou — sem erro,
     * sem log, sem sintoma. Aqui isso quebra o build.
     */
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_SUB = "110169484474386276334";
    private static final String EXPECTED_ID = "31604bcd-9f9b-35ac-be5f-c6031c7a5bc7";

    private static Jwt token(String issuer, String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("nao-importa")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600));

        if (issuer != null) builder.issuer(issuer);
        return builder.build();
    }

    @Test
    @DisplayName("o sub numerico do Google gera o mesmo id que o Order Service gera")
    void matchesTheOrderService() {
        assertThat(CustomerIdentity.of(token(GOOGLE_ISSUER, GOOGLE_SUB))).isEqualTo(EXPECTED_ID);
    }

    @Test
    @DisplayName("um sub que ja e UUID e usado como esta")
    void keepsUuidSubjectsAsTheyAre() {
        String id = UUID.fromString("3f1c9a6e-77b2-4c0d-9f31-2a5b8e4d6c10").toString();

        assertThat(CustomerIdentity.of(token(null, id))).isEqualTo(id);
    }

    @Test
    @DisplayName("o mesmo sub em provedores diferentes sao pessoas diferentes")
    void differentIssuersDoNotCollide() {
        assertThat(CustomerIdentity.of(token(GOOGLE_ISSUER, "12345")))
                .isNotEqualTo(CustomerIdentity.of(token("https://login.microsoftonline.com", "12345")));
    }
}
