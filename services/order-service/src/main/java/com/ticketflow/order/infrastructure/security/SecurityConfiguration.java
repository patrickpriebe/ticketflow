package com.ticketflow.order.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Protege a API.
 *
 * <p>Antes disso o {@code customerId} chegava no corpo da requisição, o que
 * significa que qualquer pessoa podia comprar — e consultar — como qualquer outra.
 * Agora a identidade vem do token e o corpo não tem voz nenhuma sobre quem é o
 * comprador.
 *
 * <p>O serviço é <strong>resource server</strong>: valida o token, nunca o emite.
 * Emitir é trabalho de um provedor de identidade; guardar senha aqui seria assumir
 * um risco que não pertence a este serviço.
 */
@Configuration
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            @Value("${ticketflow.auth.dev-tokens:false}") boolean devTokens) throws Exception {

        if (devTokens) {
            // O emissor de desenvolvimento precisa ser alcançável sem token - senão
            // não há como obter o primeiro. A liberação acompanha a flag: com ela
            // desligada o endpoint nem existe, e o caminho continua fechado.
            http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/dev/**").permitAll());
        }

        return http
                // Sem sessão e sem cookie, então não há o que um CSRF sequestrar.
                // Desligar CSRF numa API com cookie de sessão seria outra história.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Probes e métricas ficam abertas: o Kubernetes consulta as
                        // primeiras antes de existir qualquer token, e o Prometheus
                        // raspa a última de dentro da rede.
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        // O catálogo é público de propósito - é uma vitrine, e exigir
                        // login para ver o que está à venda afastaria comprador.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/order-service.yaml").permitAll()
                        // Comprar e consultar pedido exigem identidade.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * Decoder com segredo simétrico.
     *
     * <p>Escolha consciente para um projeto que roda localmente: apontar para um
     * provedor real é trocar este bean por
     * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}, sem tocar em
     * nenhuma outra linha do serviço. O resto do código só conhece {@code Jwt}.
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${ticketflow.auth.secret}") String secret) {
        if (secret.length() < 32) {
            // HS256 com segredo curto é criptografia de brinquedo. Falhar no boot é
            // melhor que rodar parecendo seguro.
            throw new IllegalStateException(
                    "ticketflow.auth.secret precisa de pelo menos 32 caracteres");
        }
        return NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
    }
}
