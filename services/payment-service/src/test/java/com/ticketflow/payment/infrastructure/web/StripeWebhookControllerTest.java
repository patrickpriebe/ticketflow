package com.ticketflow.payment.infrastructure.web;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.ticketflow.payment.application.port.in.SettlePaymentFromProviderUseCase;
import com.ticketflow.payment.application.port.in.SettlePaymentFromProviderUseCase.Command;
import com.ticketflow.payment.application.port.in.SettlePaymentFromProviderUseCase.Result;
import com.ticketflow.payment.domain.model.AttemptOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A verificação de assinatura do webhook.
 *
 * <p>Este é o teste mais importante do serviço de pagamento, e a razão é direta:
 * o endpoint é público — tem que ser, o Stripe precisa alcançá-lo — e o que ele
 * recebe é "o pagamento X foi aprovado". Sem conferir a assinatura, qualquer
 * pessoa com o endereço emite ingresso de graça.
 *
 * <p>Os casos cobertos não são decorativos. Assinatura errada, ausente, corpo
 * adulterado depois de assinado e segredo não configurado são exatamente as
 * quatro formas de essa proteção falhar na prática.
 */
class StripeWebhookControllerTest {

    private static final String SECRET = "whsec_teste_com_tamanho_suficiente_123456";

    private RecordingUseCase useCase;
    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        useCase = new RecordingUseCase();
        controller = new StripeWebhookController(useCase, SECRET, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("assinatura invalida e recusada com 400")
    void rejectsInvalidSignature() {
        String payload = succeededEvent("pi_forjado", "evt_forjado");

        ResponseEntity<String> response = controller.receive(payload,
                "t=" + epoch() + ",v1=0000000000000000000000000000000000000000000000000000000000000000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(useCase.commands)
                .as("um webhook nao verificado jamais pode chegar ao caso de uso")
                .isEmpty();
    }

    @Test
    @DisplayName("sem cabecalho de assinatura e recusado")
    void rejectsMissingSignature() {
        ResponseEntity<String> response = controller.receive(succeededEvent("pi_1", "evt_1"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(useCase.commands).isEmpty();
    }

    @Test
    @DisplayName("corpo adulterado depois de assinado e recusado")
    void rejectsTamperedPayload() {
        String original = succeededEvent("pi_barato", "evt_1");
        String signature = sign(original, SECRET);

        // Mesma assinatura, outro conteudo: o ataque obvio contra quem so confere
        // se o cabecalho existe.
        String tampered = original.replace("pi_barato", "pi_caro");

        ResponseEntity<String> response = controller.receive(tampered, signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(useCase.commands).isEmpty();
    }

    @Test
    @DisplayName("sem segredo configurado o endpoint nao aceita nada")
    void refusesWhenSecretIsMissing() {
        StripeWebhookController semSegredo = new StripeWebhookController(useCase, "", new SimpleMeterRegistry());

        ResponseEntity<String> response = semSegredo.receive(succeededEvent("pi_1", "evt_1"), "t=1,v1=abc");

        // 503 e nao 200: aceitar sem poder verificar seria confiar em qualquer um.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(useCase.commands).isEmpty();
    }

    @Test
    @DisplayName("assinatura valida de pagamento aprovado chega ao caso de uso")
    void acceptsValidSucceededEvent() {
        String payload = succeededEvent("pi_valido", "evt_valido");

        ResponseEntity<String> response = controller.receive(payload, sign(payload, SECRET));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(useCase.commands).hasSize(1);
        Command command = useCase.commands.get(0);
        assertThat(command.outcome()).isEqualTo(AttemptOutcome.APPROVED);
        assertThat(command.transactionId()).isEqualTo("pi_valido");
        assertThat(command.providerEventId()).isEqualTo("evt_valido");
    }

    @Test
    @DisplayName("pagamento falhado vira recusa, nao erro")
    void mapsFailedEventToRejected() {
        String payload = failedEvent("pi_recusado", "evt_recusado");

        ResponseEntity<String> response = controller.receive(payload, sign(payload, SECRET));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(useCase.commands).hasSize(1);
        assertThat(useCase.commands.get(0).outcome()).isEqualTo(AttemptOutcome.REJECTED);
    }

    @Test
    @DisplayName("evento de outro tipo e ignorado com 200")
    void ignoresUnrelatedEvents() {
        String payload = event("customer.created", "evt_outro", "{\"id\":\"cus_1\",\"object\":\"customer\"}");

        ResponseEntity<String> response = controller.receive(payload, sign(payload, SECRET));

        // 200 de proposito: responder erro faria o Stripe reenviar para sempre algo
        // que este servico nunca vai querer.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(useCase.commands).isEmpty();
    }

    // ------------------------------------------------------------------ apoio

    private static long epoch() {
        return System.currentTimeMillis() / 1000;
    }

    /** Monta o cabeçalho `Stripe-Signature` do mesmo jeito que o Stripe monta. */
    private static String sign(String payload, String secret) {
        long timestamp = epoch();
        String signedPayload = timestamp + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hex = HexFormat.of().formatHex(
                    mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String succeededEvent(String intentId, String eventId) {
        return event("payment_intent.succeeded", eventId,
                "{\"id\":\"" + intentId + "\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}");
    }

    private static String failedEvent(String intentId, String eventId) {
        return event("payment_intent.payment_failed", eventId,
                "{\"id\":\"" + intentId + "\",\"object\":\"payment_intent\","
                        + "\"status\":\"requires_payment_method\","
                        + "\"last_payment_error\":{\"code\":\"card_declined\","
                        + "\"decline_code\":\"insufficient_funds\",\"message\":\"Sem saldo\"}}");
    }

    private static String event(String type, String eventId, String dataObject) {
        return "{\"id\":\"" + eventId + "\",\"object\":\"event\",\"api_version\":\"2024-06-20\","
                + "\"type\":\"" + type + "\",\"data\":{\"object\":" + dataObject + "}}";
    }

    private static class RecordingUseCase implements SettlePaymentFromProviderUseCase {
        private final List<Command> commands = new ArrayList<>();

        @Override
        public Result execute(Command command) {
            commands.add(command);
            return command.outcome() == AttemptOutcome.APPROVED ? Result.APPROVED : Result.REJECTED;
        }
    }
}
