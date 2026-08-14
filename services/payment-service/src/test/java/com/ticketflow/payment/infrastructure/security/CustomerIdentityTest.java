package com.ticketflow.payment.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O mesmo vetor fixo do Order Service e do Notification Service.
 *
 * <p>É o terceiro lugar onde esta regra vive, e o teste existe para que divergir
 * quebre o build. A consequência de divergir aqui: o dono do pedido pediria o
 * segredo da própria cobrança e receberia "não encontrado", o cartão nunca seria
 * confirmado, e o pedido morreria expirado sem erro em lugar nenhum.
 */
class CustomerIdentityTest {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_SUB = "110169484474386276334";
    private static final String EXPECTED_ID = "31604bcd-9f9b-35ac-be5f-c6031c7a5bc7";

    private static Jwt token(String issuer, String subject) {
        return Jwt.withTokenValue("nao-importa")
                .header("alg", "none")
                .subject(subject)
                .issuer(issuer)
                .claim("sub", subject)
                .build();
    }

    @Test
    @DisplayName("o sub numerico do Google deriva no MESMO id dos outros dois servicos")
    void googleSubjectMatchesTheOtherServices() {
        assertThat(CustomerIdentity.of(token(GOOGLE_ISSUER, GOOGLE_SUB)).toString())
                .as("""
                        Vetor fixo compartilhado com AuthenticatedCustomerTest (Order) e
                        CustomerIdentityTest (Notification). Se este valor mudar em um
                        dos três, o build quebra — que é exatamente o objetivo.""")
                .isEqualTo(EXPECTED_ID);
    }

    @Test
    @DisplayName("sub que ja e UUID passa direto")
    void uuidSubjectIsUsedAsIs() {
        UUID id = UUID.fromString("3f1c9a6e-77b2-4c0d-9f31-2a5b8e4d6c10");

        assertThat(CustomerIdentity.of(token("ticketflow-dev", id.toString()))).isEqualTo(id);
    }

    @Test
    @DisplayName("o mesmo sub em emissores diferentes da pessoas diferentes")
    void issuerIsPartOfTheIdentity() {
        assertThat(CustomerIdentity.of(token("https://accounts.google.com", GOOGLE_SUB)))
                .as("Dois provedores podem usar o mesmo `sub` para pessoas diferentes; "
                        + "sem o emissor na conta, um dia isso vira a fusão de duas contas.")
                .isNotEqualTo(CustomerIdentity.of(token("https://outro-provedor.com", GOOGLE_SUB)));
    }
}
