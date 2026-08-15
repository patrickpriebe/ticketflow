package com.ticketflow.notification.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Protege a leitura dos ingressos.
 *
 * <p>Mesmo segredo que o Order Service valida — os dois são resource servers do
 * mesmo provedor. Nenhum dos dois emite token.
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            @Value("${ticketflow.observability.public-metrics:false}") boolean publicMetrics) throws Exception {

        if (publicMetrics) {
            http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/prometheus").permitAll());
        }

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Só health e info. As métricas ficam atrás da autenticação a
                        // menos que alguém liberte explicitamente — ver Order Service.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Ingresso é documento pessoal: nada aqui é público.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Mesma escolha do Order Service, e precisa continuar sendo.
     *
     * <p>Os dois validam token do mesmo emissor. Se um aceitasse o provedor e o
     * outro só o segredo local, o cliente compraria e não conseguiria ver o
     * ingresso — com 401 numa tela e nada de errado na outra.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${ticketflow.auth.issuer-uri:}") String issuerUri,
            @Value("${ticketflow.auth.audience:}") String audience,
            @Value("${ticketflow.auth.secret:}") String secret) {

        if (!issuerUri.isBlank()) {
            if (audience.isBlank()) {
                throw new IllegalStateException(
                        "ticketflow.auth.audience é obrigatório junto com issuer-uri: "
                                + "sem ele, um token emitido para outro aplicativo do mesmo "
                                + "provedor entra como login válido");
            }
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuerUri),
                    new AudienceValidator(audience)));
            return decoder;
        }

        if (secret.isBlank()) {
            throw new IllegalStateException(
                    "configure ticketflow.auth.issuer-uri (provedor de identidade) "
                            + "ou ticketflow.auth.secret (emissor local)");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("ticketflow.auth.secret precisa de pelo menos 32 caracteres");
        }
        return NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
    }
}
