package com.ticketflow.notification.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Cópia deliberada do validador do Order Service.
 *
 * <p>Os dois serviços validam token do mesmo emissor e precisam aplicar a mesma
 * regra — a duplicação segue a mesma escolha de {@link CustomerIdentity}: não há
 * módulo compartilhado neste projeto, e inventar um só para duas classes
 * acoplaria os dois serviços por uma dependência de build.
 *
 * <p>O que esta checagem impede: um ID token do Google emitido para outro
 * aplicativo tem assinatura válida e o mesmo {@code iss} que o nosso. Sem
 * comparar o {@code aud}, ele passaria — e como o {@code sub} do Google é o
 * mesmo em qualquer aplicativo, abriria a conta real da pessoa. Aqui isso
 * significaria ler o ingresso de outra pessoa.
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
        // Null quando o claim não existe, não lista vazia — sem a checagem o
        // token sem `aud` vira 500 em vez de 401.
        List<String> audiences = token.getAudience();
        return audiences != null && audiences.contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(WRONG_AUDIENCE);
    }
}
