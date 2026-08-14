package com.ticketflow.payment.infrastructure.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Quem está pedindo, a partir do token.
 *
 * <p><strong>Terceira cópia literal da mesma regra</strong> — as outras duas são
 * o {@code AuthenticatedCustomer} do Order Service e o {@code CustomerIdentity}
 * do Notification Service. Os três serviços são projetos Maven independentes,
 * sem módulo compartilhado, e a duplicação é decisão do projeto.
 *
 * <p>O que não é opcional é o resultado bater nos três. Aqui a consequência de
 * divergir é diferente das outras duas e vale escrever: este id decide se o
 * {@code client_secret} de uma cobrança sai ou não. Derivar diferente do Order
 * Service faria o dono do pedido receber "não encontrado" para a própria compra
 * — o cartão nunca seria confirmado, e o pedido morreria expirado sem nenhum
 * erro em lugar nenhum.
 *
 * <p>Os três lados têm um teste com o mesmo vetor fixo, justamente para que
 * divergir quebre o build em vez de sumir com dinheiro em produção.
 */
public final class CustomerIdentity {

    private CustomerIdentity() {
    }

    public static UUID of(Jwt token) {
        String subject = token.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("o token não traz 'sub'");
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException notAUuid) {
            String issuer = token.getIssuer() == null ? "" : token.getIssuer().toString();
            return UUID.nameUUIDFromBytes((issuer + "|" + subject).getBytes(StandardCharsets.UTF_8));
        }
    }
}
