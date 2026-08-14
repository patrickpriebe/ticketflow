package com.ticketflow.payment.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Exige que o token tenha sido emitido <em>para esta aplicação</em>.
 *
 * <p>É a validação que falta quando alguém liga um provedor externo e acha que
 * terminou. Assinatura válida e emissor correto provam que o Google emitiu o
 * token — não provam que ele foi emitido para nós. O Google assina token para
 * todo mundo: qualquer pessoa com um aplicativo registrado consegue um token
 * legítimo, assinado pela mesma chave, com o mesmo {@code iss}.
 *
 * <p>Sem esta checagem, esse token de outro aplicativo entra aqui como login
 * válido, e a conta que ele abre é a conta real daquela pessoa no TicketFlow —
 * porque o {@code sub} do Google é o mesmo em qualquer aplicativo. Quem
 * controlasse qualquer site com "entrar com Google" poderia comprar no nome de
 * quem entrasse lá.
 *
 * <p>O {@code aud} do ID token é o client id que pediu a emissão. Comparar com o
 * nosso é o que fecha isso.
 */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error WRONG_AUDIENCE = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "o token nao foi emitido para esta aplicacao",
            null);

    private final String audience;

    public AudienceValidator(String audience) {
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("audience é obrigatório");
        }
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        // getAudience() devolve null quando o token não traz o claim — não uma
        // lista vazia. Sem esta checagem o caso vira NullPointerException, e o
        // token sem `aud` recebe 500 em vez de 401: a requisição é recusada de
        // qualquer jeito, mas pelo motivo errado e com a aparência de defeito
        // do servidor. Foi o teste que apontou isso.
        List<String> audiences = token.getAudience();
        return audiences != null && audiences.contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(WRONG_AUDIENCE);
    }
}
