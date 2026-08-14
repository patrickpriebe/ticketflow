package com.ticketflow.payment.infrastructure.web;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.ticketflow.payment.application.port.in.SettlePaymentFromProviderUseCase;
import com.ticketflow.payment.application.port.in.SettlePaymentFromProviderUseCase.Command;
import com.ticketflow.payment.domain.model.AttemptOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Onde o Stripe avisa que uma cobrança terminou.
 *
 * <p><strong>A verificação de assinatura é a única coisa que separa este endpoint
 * de uma porta aberta para forjar aprovações.</strong> Ele é público — tem que
 * ser, o Stripe precisa alcançá-lo — e recebe "o pagamento X foi aprovado". Sem
 * conferir a assinatura, qualquer pessoa com o endereço emite ingresso de graça.
 *
 * <p>O corpo é lido como {@code String} de propósito. A assinatura é um HMAC
 * sobre os <em>bytes exatos</em> que o Stripe enviou; deixar o Jackson
 * desserializar e depois re-serializar mudaria espaços ou ordem de campos e a
 * verificação falharia sem motivo aparente.
 *
 * <p>Devolve 200 para tudo que foi entendido, mesmo quando não havia o que fazer.
 * O provedor reenvia enquanto não receber 200, e responder erro para um evento
 * que nunca vai mudar cria uma retentativa eterna.
 */
@RestController
@RequestMapping("/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
    private static final String PROVIDER = "stripe";

    private final SettlePaymentFromProviderUseCase settlePayment;
    private final String webhookSecret;
    private final MeterRegistry registry;

    public StripeWebhookController(SettlePaymentFromProviderUseCase settlePayment,
                                   @Value("${ticketflow.stripe.webhook-secret}") String webhookSecret,
                                   MeterRegistry registry) {
        this.settlePayment = settlePayment;
        this.webhookSecret = webhookSecret;
        this.registry = registry;
    }

    /**
     * Conta cada webhook pelo que aconteceu com ele.
     *
     * <p>Sem isto, assinatura sendo recusada em massa é invisível daqui: o log
     * tem a linha, mas ninguém lê log que não dispara nada. E o sintoma do lado
     * de fora é o pior possível — pedidos parando de sair de PENDING, sem erro
     * em lugar nenhum, porque a resposta do provedor está batendo na porta e
     * sendo recusada.
     *
     * <p>Um rótulo por desfecho, e não um contador por tipo: o que se quer ver
     * num gráfico é a proporção entre aceito e recusado.
     */
    private void count(String outcome) {
        registry.counter("ticketflow.webhook.received", "provider", PROVIDER, "outcome", outcome)
                .increment();
    }

    @PostMapping
    public ResponseEntity<String> receive(@RequestBody String payload,
                                          @RequestHeader(value = "Stripe-Signature", required = false)
                                          String signature) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            // Sem segredo não há como verificar nada, e aceitar seria pior do que
            // recusar: o serviço passaria a confiar em qualquer requisição.
            log.error("Webhook recebido sem ticketflow.stripe.webhook-secret configurado");
            count("nao_configurado");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("webhook nao configurado");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            // 400 e nada mais. Nenhum log do corpo: se a assinatura não confere, o
            // conteúdo é de origem desconhecida e não merece ir para o log.
            log.warn("Webhook com assinatura invalida recusado");
            count("assinatura_invalida");
            return ResponseEntity.badRequest().body("assinatura invalida");
        } catch (Exception e) {
            log.warn("Webhook com corpo ilegivel recusado: {}", e.getMessage());
            count("corpo_invalido");
            return ResponseEntity.badRequest().body("corpo invalido");
        }

        return switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                count("aprovado");
                yield settle(event, AttemptOutcome.APPROVED);
            }
            case "payment_intent.payment_failed" -> {
                count("recusado");
                yield settle(event, AttemptOutcome.REJECTED);
            }
            // Todo o resto é ruído para este serviço. 200 para o Stripe parar de
            // reenviar; ignorar sem responder faria a fila dele crescer à toa.
            default -> {
                count("ignorado");
                yield ResponseEntity.ok("ignorado: " + event.getType());
            }
        };
    }

    /**
     * Extrai o PaymentIntent do evento, com um caminho de recuo.
     *
     * <p>O SDK se recusa a desserializar quando a {@code api_version} do evento
     * difere da que ele conhece — e isso não é hipótese: a versão vem fixada na
     * conta do Stripe e segue a própria vida, enquanto o SDK só muda quando
     * alguém atualiza o pom. Tratar isso como "evento ilegível" faria pagamento
     * aprovado nunca chegar ao pedido, e o sintoma seria pedido eternamente
     * pendente, sem erro em lugar nenhum.
     *
     * <p>O recuo é a saída documentada pelo próprio Stripe para esse caso. É
     * seguro aqui porque só três campos são lidos — id, status e o último erro — e
     * esses não mudam de forma entre versões.
     */
    private Optional<PaymentIntent> paymentIntentOf(Event event) {
        var deserializer = event.getDataObjectDeserializer();

        Optional<PaymentIntent> matched = deserializer.getObject()
                .filter(PaymentIntent.class::isInstance)
                .map(PaymentIntent.class::cast);
        if (matched.isPresent()) {
            return matched;
        }

        try {
            StripeObject unsafe = deserializer.deserializeUnsafe();
            log.debug("Evento {} lido pelo caminho de recuo (versao de API diferente)", event.getId());
            return unsafe instanceof PaymentIntent intent ? Optional.of(intent) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private ResponseEntity<String> settle(Event event, AttemptOutcome outcome) {
        Optional<PaymentIntent> intent = paymentIntentOf(event);

        if (intent.isEmpty()) {
            // 200 mesmo assim: reenviar não vai fazer o SDK entender melhor.
            log.warn("Evento {} nao pode ser desserializado nesta versao do SDK", event.getId());
            return ResponseEntity.ok("evento nao interpretavel");
        }

        PaymentIntent paymentIntent = intent.get();
        String failureCode = null;
        String failureReason = null;
        if (paymentIntent.getLastPaymentError() != null) {
            failureCode = paymentIntent.getLastPaymentError().getDeclineCode() != null
                    ? paymentIntent.getLastPaymentError().getDeclineCode()
                    : paymentIntent.getLastPaymentError().getCode();
            failureReason = paymentIntent.getLastPaymentError().getMessage();
        }

        var result = settlePayment.execute(new Command(
                PROVIDER,
                event.getId(),
                event.getType(),
                paymentIntent.getId(),
                outcome,
                failureCode,
                failureReason,
                // Só o estado, nunca o corpo inteiro: ele carrega dados do pagador.
                paymentIntent.getStatus()));

        return ResponseEntity.ok(result.name());
    }
}
