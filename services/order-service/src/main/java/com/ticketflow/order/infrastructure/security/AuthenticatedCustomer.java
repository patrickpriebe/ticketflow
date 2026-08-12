package com.ticketflow.order.infrastructure.security;

import com.ticketflow.order.domain.model.Customer;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Traduz o token no cliente do domínio.
 *
 * <p>O ponto inteiro deste tipo: o {@code customerId} sai do {@code sub} do token
 * assinado, e não de um campo que o chamador escolheu. Enquanto vinha do corpo,
 * trocar um UUID era suficiente para comprar no nome de outra pessoa.
 */
public final class AuthenticatedCustomer {

    private AuthenticatedCustomer() {
    }

    public static Customer from(Jwt token) {
        UUID id;
        try {
            id = UUID.fromString(token.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("o 'sub' do token não é um UUID de cliente");
        }

        String email = token.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new InvalidTokenException("o token não traz o e-mail do cliente");
        }

        String name = token.getClaimAsString("name");
        return new Customer(id, name == null || name.isBlank() ? "Cliente" : name, email);
    }

    /** Token válido na assinatura, mas sem o que a compra precisa. */
    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super("Token inválido: " + message);
        }
    }
}
