package com.ticketflow.order.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
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
            @Value("${ticketflow.auth.dev-tokens:false}") boolean devTokens,
            @Value("${ticketflow.observability.public-metrics:false}") boolean publicMetrics) throws Exception {

        if (publicMetrics) {
            // Liberado só onde existe raspador: o Prometheus do compose e o do
            // cluster. Aberto por toda parte, `/actuator/prometheus` entrega de
            // graça a quem passar o volume de pedidos, quanto foi aprovado e
            // recusado, cada rota da API e a versão exata da JVM — um mapa do
            // sistema para quem estiver escolhendo por onde começar.
            //
            // O padrão é fechado pelo mesmo motivo que `dev-tokens` é: uma
            // variável esquecida tem que falhar para o lado seguro. Nada raspa
            // estes serviços no Render, então lá não se perde nada fechando.
            http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/prometheus").permitAll());
        }

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
                        // As probes ficam abertas: o Kubernetes e o Render as
                        // consultam antes de existir qualquer token. As métricas
                        // não — elas dependem da liberação acima.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
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
     * Um dos dois emissores, escolhido pela configuração.
     *
     * <p>Com {@code issuer-uri} definido, o serviço valida token de um provedor
     * de verdade — o Google, em produção: busca a chave pública no JWKS dele,
     * confere emissor, validade e {@code audience}. Sem ele, cai no segredo
     * simétrico local.
     *
     * <p>Os dois modos existem de propósito. Exigir uma conta no Google para
     * rodar o projeto seria atrito puro para quem clona o repositório, e o
     * caminho local não pode ser o mesmo de produção — lá o token é emitido sem
     * senha nenhuma. O resto do serviço não sabe qual dos dois está em uso,
     * porque só conhece {@code Jwt}.
     *
     * <p>A promessa que estas linhas sustentam: <strong>não existe configuração
     * que suba o serviço aceitando token sem verificação</strong>. Faltando os
     * dois, o boot cai. Com provedor e sem {@code audience}, o boot cai também —
     * ver {@link AudienceValidator} para o porquê de aceitar qualquer
     * {@code audience} ser tão ruim quanto não verificar assinatura.
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

            // withIssuerLocation faz a descoberta OIDC no boot e monta o decoder a
            // partir do que o provedor publica. Custa uma chamada de rede na
            // subida e paga com o oposto de um JWKS fixo no código: se o provedor
            // rotacionar a chave ou mudar o endpoint, nada aqui precisa mudar.
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    // Emissor e validade. O emissor entrar aqui também protege a
                    // identidade: AuthenticatedCustomer deriva o id de `issuer|sub`,
                    // então aceitar duas grafias do mesmo emissor daria dois ids
                    // diferentes para a mesma pessoa.
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
