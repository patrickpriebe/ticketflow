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
    @DisplayName("o segredo de assinatura nao tem valor padrao")
    void signingSecretHasNoDefault() throws IOException {
        assertThat(baseDocument())
                .as("""
                        ticketflow.auth.secret não pode ter padrão na configuração
                        base: um ambiente que suba com a chave de exemplo — que está
                        publicada neste repositório — aceita token forjado por
                        qualquer pessoa. Sem padrão, o boot falha alto, que é o
                        comportamento desejado.""")
                .contains("secret: ${TICKETFLOW_AUTH_SECRET}")
                .doesNotContain("secret: ${TICKETFLOW_AUTH_SECRET:");
    }
}
