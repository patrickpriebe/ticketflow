package com.ticketflow.order.infrastructure.security;

import com.ticketflow.order.domain.model.Customer;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
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
        UUID id = customerId(token);

        String email = token.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new InvalidTokenException("o token não traz o e-mail do cliente");
        }

        String name = token.getClaimAsString("name");
        return new Customer(id, name == null || name.isBlank() ? "Cliente" : name, email);
    }

    /**
     * O identificador interno do cliente, a partir do token.
     *
     * <p>O domínio usa UUID e vai continuar usando — mas exigir que o provedor de
     * identidade também use era acoplamento escondido. O Google devolve um
     * {@code sub} numérico como {@code 110169484474386276334}, e enquanto isto
     * aqui fazia {@code UUID.fromString} direto, todo login real derrubaria
     * qualquer requisição autenticada.
     *
     * <p>Quando o {@code sub} já é um UUID — o caso do emissor local — ele é usado
     * como está. Quando não é, o id sai de {@code issuer|sub}: o emissor entra na
     * conta porque dois provedores diferentes podem usar o mesmo {@code sub} para
     * pessoas diferentes, e sem ele um dia isso vira a fusão de duas contas.
     *
     * <p>Derivar em vez de guardar uma tabela {@code customers} é escolha
     * consciente e tem um custo conhecido: trocar de provedor de identidade
     * significa trocar o id de todo mundo. Uma tabela de mapeamento é o próximo
     * passo se este projeto tiver de sobreviver a essa troca.
     */
    static UUID customerId(Jwt token) {
        String subject = token.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidTokenException("o token não traz 'sub'");
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException notAUuid) {
            String issuer = token.getIssuer() == null ? "" : token.getIssuer().toString();
            return UUID.nameUUIDFromBytes((issuer + "|" + subject).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Token válido na assinatura, mas sem o que a compra precisa. */
    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super("Token inválido: " + message);
        }
    }
}
