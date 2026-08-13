package com.ticketflow.notification.infrastructure.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Quem é o dono do ingresso, a partir do token.
 *
 * <p><strong>Esta regra é cópia literal do {@code AuthenticatedCustomer} do Order
 * Service, e precisa continuar sendo.</strong> Os dois serviços são projetos Maven
 * independentes, sem módulo compartilhado — decisão do projeto — então a
 * duplicação é deliberada. O que não é opcional é o resultado bater: o Order
 * grava o pedido com o id que ele derivou, o evento carrega esse id, e o ingresso
 * nasce com ele. Se este arquivo divergir, o cliente autentica normalmente e vê
 * uma lista de ingressos vazia, sem erro nenhum na tela nem no log.
 *
 * <p>Por isso os dois lados têm um teste com o mesmo vetor fixo: divergir passa a
 * quebrar o build em vez de sumir com ingresso em produção.
 */
public final class CustomerIdentity {

    private CustomerIdentity() {
    }

    public static String of(Jwt token) {
        String subject = token.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("o token não traz 'sub'");
        }

        try {
            return UUID.fromString(subject).toString();
        } catch (IllegalArgumentException notAUuid) {
            String issuer = token.getIssuer() == null ? "" : token.getIssuer().toString();
            return UUID.nameUUIDFromBytes((issuer + "|" + subject).getBytes(StandardCharsets.UTF_8))
                    .toString();
        }
    }
}
