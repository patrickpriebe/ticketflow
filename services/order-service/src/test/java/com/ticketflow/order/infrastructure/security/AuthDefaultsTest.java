package com.ticketflow.order.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava os padrões de autenticação da configuração base.
 *
 * <p>Teste de arquivo de configuração é incomum, e aqui se justifica pelo tipo de
 * defeito que ele impede: um padrão perigoso não quebra teste nenhum, não aparece
 * em revisão de código e só se manifesta como incidente. Estas duas linhas já
 * estiveram erradas — {@code dev-tokens} valia {@code true} e o segredo tinha um
 * padrão publicado no repositório —, o que significava que subir para qualquer
 * lugar sem lembrar de duas variáveis deixava um emissor de identidade aberto na
 * internet e uma chave de assinatura de conhecimento público.
 *
 * <p>Só o primeiro documento YAML é inspecionado, que é o que vale para qualquer
 * perfil. Os perfis {@code local} e {@code docker} definem valores frouxos de
 * propósito e não são olhados aqui.
 */
class AuthDefaultsTest {

    private static String baseDocument() throws IOException {
        try (InputStream in = AuthDefaultsTest.class.getResourceAsStream("/application.yml")) {
            String whole = new String(Objects.requireNonNull(in, "application.yml não está no classpath")
                    .readAllBytes(), StandardCharsets.UTF_8);
            // Os perfis vêm depois do primeiro separador de documento.
            int firstSeparator = whole.indexOf("\n---");
            return firstSeparator < 0 ? whole : whole.substring(0, firstSeparator);
        }
    }

    @Test
    @DisplayName("o emissor de token de desenvolvimento vem desligado por padrao")
    void devTokensDefaultsToOff() throws IOException {
        assertThat(baseDocument())
                .as("""
                        dev-tokens precisa vir desligado na configuração base.
                        Ligado, o endpoint que emite token sem senha para qualquer
                        e-mail fica público em qualquer ambiente que esqueça a
                        variável — e esquecer é o caso comum.""")
                .contains("dev-tokens: ${TICKETFLOW_DEV_TOKENS:false}");
    }

    @Test
    @DisplayName("a rota de metricas nao vem publica na configuracao base")
    void publicMetricsDefaultsToOff() throws IOException {
        assertThat(baseDocument())
                .as("""
                        A configuração base não pode liberar /actuator/prometheus.

                        Ela já esteve aberta sem condição nenhuma, e no Render isso
                        entrega a qualquer pessoa o volume de pedidos, quanto foi
                        aprovado e recusado, cada rota da API e a versão exata da JVM
                        — um mapa do sistema para quem estiver escolhendo por onde
                        começar. Lá ninguém raspa essas métricas, então fechar não
                        custa nada.

                        O padrão vive no SecurityConfiguration e vale `false`. Os
                        perfis `local` e `docker` ligam de propósito, porque ali existe
                        um Prometheus do outro lado.""")
                .doesNotContain("public-metrics");
    }

    @Test
    @DisplayName("o segredo de assinatura nao tem chave de exemplo na configuracao base")
    void signingSecretHasNoUsableDefault() throws IOException {
        assertThat(baseDocument())
                .as("""
                        A configuração base não pode entregar uma chave de assinatura
                        pronta. A chave de exemplo está publicada neste repositório, e
                        um ambiente que suba com ela aceita token forjado por qualquer
                        pessoa.

                        O padrão vazio é o que existe aqui, e não é um relaxamento: com
                        provedor de identidade configurado o segredo simétrico não é
                        usado, e sem provedor nem segredo o boot cai — quem garante isso
                        agora é SecurityConfiguration, coberto por
                        JwtDecoderSelectionTest.""")
                .contains("secret: ${TICKETFLOW_AUTH_SECRET:}")
                .doesNotContain("desenvolvimento-local");
    }
}
