package com.ticketflow.payment.infrastructure.security;

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
 * Protege a consulta da cobrança.
 *
 * <p>Este serviço passou anos sem camada de segurança porque só falava com o
 * Kafka e com o provedor. Isso mudou quando o navegador precisou buscar aqui o
 * {@code client_secret} para confirmar o cartão: é dado de um cliente
 * específico, e quem pergunta tem que provar quem é.
 *
 * <p>O webhook fica de fora da autenticação de propósito — quem chama é o
 * Stripe, que não tem token nosso. Ele se autentica de outro jeito: assinatura
 * HMAC sobre o corpo cru, verificada no controller. Trocar isso por um token
 * seria pior, não melhor.
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
                        // Só health e info. As métricas deste serviço são as mais
                        // sensíveis dos três — receita aprovada e recusada por método
                        // de pagamento — e ficam fechadas por padrão.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Autenticado pela assinatura do Stripe, não por token.
                        .requestMatchers("/webhooks/stripe").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * O mesmo decoder dos outros dois serviços, e precisa continuar sendo: os
     * três validam token do mesmo emissor. Ver a nota no Order Service para o
     * porquê de o boot cair quando falta {@code audience}.
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
