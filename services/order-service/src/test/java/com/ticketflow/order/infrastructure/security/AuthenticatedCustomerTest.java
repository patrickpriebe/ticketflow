package com.ticketflow.order.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedCustomerTest {

    /**
     * O mesmo vetor existe no {@code CustomerIdentityTest} do Notification Service.
     *
     * <p>Os dois serviços derivam o id do cliente por conta própria — são projetos
     * independentes, sem módulo compartilhado. Se as duas regras divergirem, o
     * Order grava o pedido com um id e o Notification procura o ingresso com
     * outro: o cliente vê uma lista vazia, sem erro em lugar nenhum. Este literal
     * é o que transforma essa divergência em build quebrado.
     */
    static final String GOOGLE_ISSUER = "https://accounts.google.com";
    static final String GOOGLE_SUB = "110169484474386276334";
    static final String EXPECTED_ID = "31604bcd-9f9b-35ac-be5f-c6031c7a5bc7";

    private static Jwt token(String issuer, String subject, String email, String name) {
        Jwt.Builder builder = Jwt.withTokenValue("nao-importa")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600));

        if (issuer != null) builder.issuer(issuer);
        if (email != null) builder.claim("email", email);
        if (name != null) builder.claim("name", name);
        return builder.build();
    }

    @Test
    @DisplayName("um sub que ja e UUID e usado como esta")
    void keepsUuidSubjectsAsTheyAre() {
        UUID id = UUID.fromString("3f1c9a6e-77b2-4c0d-9f31-2a5b8e4d6c10");

        assertThat(AuthenticatedCustomer.customerId(token(null, id.toString(), "ana@example.com", "Ana")))
                .isEqualTo(id);
    }

    @Test
    @DisplayName("o sub numerico do Google vira um id estavel, e nao uma excecao")
    void derivesAnIdFromGoogleSubject() {
        Jwt google = token(GOOGLE_ISSUER, GOOGLE_SUB, "ana@example.com", "Ana");

        assertThat(AuthenticatedCustomer.customerId(google))
                .hasToString(EXPECTED_ID);
    }

    @Test
    @DisplayName("o mesmo sub devolve sempre o mesmo id")
    void isStableAcrossCalls() {
        Jwt first = token(GOOGLE_ISSUER, GOOGLE_SUB, "ana@example.com", "Ana");
        Jwt second = token(GOOGLE_ISSUER, GOOGLE_SUB, "ana@example.com", "Ana");

        assertThat(AuthenticatedCustomer.customerId(first))
                .isEqualTo(AuthenticatedCustomer.customerId(second));
    }

    @Test
    @DisplayName("o mesmo sub em provedores diferentes sao pessoas diferentes")
    void differentIssuersDoNotCollide() {
        Jwt google = token(GOOGLE_ISSUER, "12345", "ana@example.com", "Ana");
        Jwt outro = token("https://login.microsoftonline.com", "12345", "ana@example.com", "Ana");

        assertThat(AuthenticatedCustomer.customerId(google))
                .isNotEqualTo(AuthenticatedCustomer.customerId(outro));
    }

    @Test
    @DisplayName("token sem e-mail nao serve para comprar")
    void rejectsTokenWithoutEmail() {
        Jwt semEmail = token(GOOGLE_ISSUER, GOOGLE_SUB, null, "Ana");

        assertThatThrownBy(() -> AuthenticatedCustomer.from(semEmail))
                .isInstanceOf(AuthenticatedCustomer.InvalidTokenException.class)
                .hasMessageContaining("e-mail");
    }

    @Test
    @DisplayName("sem nome no token o cliente ainda existe")
    void fallsBackWhenNameIsMissing() {
        assertThat(AuthenticatedCustomer.from(token(GOOGLE_ISSUER, GOOGLE_SUB, "ana@example.com", null)).name())
                .isEqualTo("Cliente");
    }
}
