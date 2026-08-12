package com.ticketflow.order.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DevTokenControllerTest {

    @Test
    @DisplayName("o mesmo e-mail devolve sempre o mesmo cliente")
    void sameEmailKeepsTheSameCustomer() {
        UUID first = DevTokenController.stableIdFor("ana.souza@example.com");
        UUID second = DevTokenController.stableIdFor("ana.souza@example.com");

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("e-mails diferentes sao clientes diferentes")
    void differentEmailsAreDifferentCustomers() {
        assertThat(DevTokenController.stableIdFor("ana.souza@example.com"))
                .isNotEqualTo(DevTokenController.stableIdFor("bruno.lima@example.com"));
    }

    @Test
    @DisplayName("caixa e espaco em volta nao criam um cliente novo")
    void normalisesBeforeHashing() {
        UUID canonical = DevTokenController.stableIdFor("ana.souza@example.com");

        assertThat(DevTokenController.stableIdFor("  Ana.Souza@Example.com  ")).isEqualTo(canonical);
    }

    /**
     * Este era o defeito: cada entrada gerava um id aleatório, os pedidos ficavam
     * presos ao id anterior e a tela de "meus pedidos" aparecia vazia depois de
     * sair e voltar com o mesmo e-mail.
     */
    @Test
    @DisplayName("o id nao e aleatorio entre chamadas")
    void isNotRandom() {
        assertThat(DevTokenController.stableIdFor("qualquer@example.com"))
                .isNotEqualTo(UUID.randomUUID());
    }
}
