package com.ticketflow.payment.infrastructure.gateway;

import com.ticketflow.payment.application.port.out.PaymentGateway;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * O provedor de pagamento de verdade.
 *
 * <p>Convive com o {@link HttpPaymentGateway} em vez de substituí-lo: o simulado
 * continua servindo o ambiente local, então quem clona o projeto roda o sistema
 * inteiro sem precisar de conta em lugar nenhum. Qual dos dois é montado sai da
 * configuração, e o caso de uso não sabe a diferença — é o ponto de existir uma
 * porta.
 *
 * <p><strong>Os dois métodos suportados terminam de formas diferentes, e isso é o
 * mais interessante daqui:</strong>
 *
 * <ul>
 *   <li><strong>cartão</strong> — criado e confirmado na mesma chamada, com
 *       resposta imediata: aprovado ou recusado;</li>
 *   <li><strong>boleto</strong> — só criado. Ninguém sabe se foi pago até o
 *       cliente ir ao banco, e a resposta chega por webhook, dias depois.</li>
 * </ul>
 *
 * <p>É por isso que {@code ACCEPTED} precisou existir no domínio. Sem ele, o
 * boleto teria que ser mentido como aprovado ou como falha.
 */
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    private final StripeClient stripe;
    private final MeterRegistry registry;
    /**
     * Meio de pagamento de teste usado para confirmar o cartão sem coletar dados
     * reais. Em produção este campo fica vazio e a confirmação acontece no
     * navegador, pelo Stripe Elements — o cartão nunca passa por este serviço.
     */
    private final String testCardPaymentMethod;

    public StripePaymentGateway(StripeClient stripe, MeterRegistry registry,
                                String testCardPaymentMethod) {
        this.stripe = Objects.requireNonNull(stripe, "stripe client is required");
        this.registry = Objects.requireNonNull(registry, "registry is required");
        this.testCardPaymentMethod = testCardPaymentMethod;
    }

    @Override
    public AuthorizationResponse authorize(AuthorizationRequest request) {
        Timer.Sample sample = Timer.start(registry);
        long startedAt = System.nanoTime();

        try {
            PaymentIntent intent = stripe.paymentIntents().create(
                    buildParams(request),
                    // A chave de idempotência é o id do pagamento, estável entre
                    // reentregas da mesma mensagem. Sem ela, uma reentrega do Kafka
                    // viraria uma segunda cobrança.
                    RequestOptions.builder().setIdempotencyKey(request.idempotencyKey()).build());

            int latency = latencyMs(startedAt);
            sample.stop(timer("ok"));
            return interpret(intent, latency);

        } catch (CardException e) {
            // Recusa: o provedor respondeu, e a resposta foi não. Não é falha de
            // comunicação, e tratar como tal faria o sistema tentar de novo um
            // cartão que já foi negado.
            int latency = latencyMs(startedAt);
            sample.stop(timer("rejected"));
            log.info("Stripe recusou o pagamento {}: {} ({})",
                    request.paymentId(), e.getDeclineCode(), e.getCode());
            return AuthorizationResponse.rejected(
                    e.getDeclineCode() == null ? e.getCode() : e.getDeclineCode(),
                    e.getStripeError() == null ? e.getMessage() : e.getStripeError().getMessage(),
                    e.getStatusCode() == null ? 402 : e.getStatusCode(),
                    latency, null);

        } catch (ApiConnectionException e) {
            // Não houve resposta. Se a cobrança aconteceu, ninguém sabe — e é
            // exatamente por isso que isto não pode virar recusa.
            int latency = latencyMs(startedAt);
            sample.stop(timer("timeout"));
            log.warn("Stripe nao respondeu para o pagamento {}: {}", request.paymentId(), e.getMessage());
            return AuthorizationResponse.timedOut(e.getMessage(), latency);

        } catch (StripeException e) {
            int latency = latencyMs(startedAt);
            sample.stop(timer("error"));
            log.error("Stripe respondeu com erro para o pagamento {}: {}", request.paymentId(), e.getMessage());
            return AuthorizationResponse.errored(e.getMessage(),
                    e.getStatusCode(), latency, trace("erro", e.getCode()));
        }
    }

    /**
     * Traduz o estado do PaymentIntent para o vocabulário do domínio.
     *
     * <p>{@code requires_action} e {@code requires_payment_method} não são falhas:
     * é o boleto esperando ser pago, ou o cartão esperando o 3-D Secure. O que os
     * dois têm em comum é que a resposta virá por webhook.
     */
    private AuthorizationResponse interpret(PaymentIntent intent, int latencyMs) {
        String status = intent.getStatus();
        String raw = trace(status, intent.getId());
        return switch (status) {
            case "succeeded" -> AuthorizationResponse.approved(intent.getId(), 200, latencyMs, raw);
            case "canceled" -> AuthorizationResponse.rejected(
                    "payment_intent_canceled", "O provedor cancelou a cobranca", 200, latencyMs, raw);
            case "requires_payment_method", "requires_action", "requires_confirmation", "processing" ->
                    AuthorizationResponse.accepted(intent.getId(), 200, latencyMs, raw);
            // Estado novo na API. Tratar como erro deixa a mensagem ser reentregue
            // em vez de decidir errado sobre dinheiro.
            default -> AuthorizationResponse.errored(
                    "Estado desconhecido do PaymentIntent: " + status, 200, latencyMs, raw);
        };
    }

    /**
     * O rastro guardado na tentativa.
     *
     * <p>Precisa ser JSON válido: a coluna {@code payment_attempts.response_payload}
     * é do tipo {@code json}, e o Postgres recusa uma string solta com "invalid
     * input syntax for type json" — erro que aponta para o banco quando a causa
     * está aqui.
     *
     * <p>Só estado e identificador. O PaymentIntent inteiro traria dados do
     * pagador para dentro da nossa tabela sem necessidade nenhuma.
     */
    static String trace(String status, String intentId) {
        return "{\"status\":\"" + escape(status) + "\",\"paymentIntent\":\"" + escape(intentId) + "\"}";
    }

    /** Valores vêm do provedor; escapar é barato e evita gerar JSON quebrado. */
    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private PaymentIntentCreateParams buildParams(AuthorizationRequest request) {
        Map<String, Object> body = request.body();
        String method = String.valueOf(body.get("method"));

        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(toMinorUnits(body.get("amount")))
                .setCurrency(String.valueOf(body.get("currency")).toLowerCase())
                // Metadados são o que permite reconciliar depois: dado o
                // PaymentIntent, saber qual pedido ele pagou sem consultar nada.
                .putMetadata("orderId", String.valueOf(body.get("reference")))
                .putMetadata("paymentId", request.paymentId().toString())
                .setDescription("TicketFlow - pedido " + body.get("reference"));

        if ("credit_card".equals(method)) {
            params.addPaymentMethodType("card");
            if (testCardPaymentMethod != null && !testCardPaymentMethod.isBlank()) {
                // Ambiente de teste: confirma na hora com um meio de pagamento
                // fictício do Stripe, para o ciclo fechar sem navegador.
                params.setPaymentMethod(testCardPaymentMethod).setConfirm(true);
            }
        } else if ("boleto".equals(method)) {
            // Sem confirmar de propósito: o boleto é gerado e pago depois. Confirmar
            // aqui exigiria CPF do pagador, que este serviço não coleta e não quer.
            params.addPaymentMethodType("boleto");
        } else {
            // O registro de estratégias já falha no boot se um método não tiver
            // implementação; isto cobre o caso de existir estratégia sem suporte no
            // provedor — PIX, hoje, nesta conta.
            throw new UnsupportedPaymentMethodException(method);
        }

        return params.build();
    }

    /**
     * Reais para centavos.
     *
     * <p>{@code longValueExact} de propósito: se sobrar fração depois de mover a
     * vírgula, o valor não representa centavos e algo está errado a montante.
     * Arredondar em silêncio aqui é como se cobra alguém a mais.
     */
    static long toMinorUnits(Object amount) {
        BigDecimal value = amount instanceof BigDecimal decimal
                ? decimal
                : new BigDecimal(String.valueOf(amount));
        return value.movePointRight(2).longValueExact();
    }

    private int latencyMs(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000);
    }

    private Timer timer(String outcome) {
        return Timer.builder("ticketflow.gateway.authorize")
                .tag("gateway", "stripe")
                .tag("outcome", outcome)
                .register(registry);
    }

    /** Método que existe no domínio mas não está habilitado no provedor. */
    public static class UnsupportedPaymentMethodException extends RuntimeException {
        public UnsupportedPaymentMethodException(String method) {
            super("O provedor de pagamento nao suporta o metodo '" + method + "'");
        }
    }

    /** Só para o log de diagnóstico não precisar do corpo inteiro. */
    static Map<String, Object> summarise(AuthorizationRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("paymentId", request.paymentId());
        summary.put("method", request.body().get("method"));
        summary.put("amount", request.body().get("amount"));
        return summary;
    }
}
