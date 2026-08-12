package com.ticketflow.order.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emite tokens para desenvolvimento local.
 *
 * <p><strong>Isto não é um provedor de identidade e não deve virar um.</strong> Não
 * há senha, não há verificação de nada: qualquer um pede um token para qualquer
 * cliente. Existe só para que o front local e o curl consigam exercitar a API sem
 * subir um Keycloak.
 *
 * <p>Por isso o bean só existe com {@code ticketflow.auth.dev-tokens=true}, que é
 * falso por padrão. Num ambiente real o token viria do provedor e este arquivo
 * simplesmente não estaria no classpath ativo.
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "ticketflow.auth.dev-tokens", havingValue = "true")
public class DevTokenController {

    private static final Duration LIFETIME = Duration.ofHours(8);

    private final String secret;

    public DevTokenController(@Value("${ticketflow.auth.secret}") String secret) {
        this.secret = secret;
    }

    @PostMapping("/token")
    public TokenResponse issue(@RequestBody(required = false) TokenRequest request) {
        TokenRequest payload = request == null ? TokenRequest.anonymous() : request;
        UUID customerId = payload.customerId() == null ? UUID.randomUUID() : payload.customerId();
        Instant now = Instant.now();

        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(customerId.toString())
                    .claim("name", payload.nameOrDefault())
                    .claim("email", payload.emailOrDefault())
                    .issuer("ticketflow-dev")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(LIFETIME)))
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));

            return new TokenResponse(jwt.serialize(), customerId, payload.nameOrDefault(),
                    payload.emailOrDefault(), LIFETIME.toSeconds());

        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao assinar o token de desenvolvimento", e);
        }
    }

    public record TokenRequest(UUID customerId, String name, String email) {

        static TokenRequest anonymous() {
            return new TokenRequest(null, null, null);
        }

        String nameOrDefault() {
            return name == null || name.isBlank() ? "Ana Souza" : name;
        }

        String emailOrDefault() {
            return email == null || email.isBlank() ? "ana.souza@example.com" : email;
        }
    }

    public record TokenResponse(String token, UUID customerId, String name, String email, long expiresInSeconds) {
    }
}
