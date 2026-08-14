package com.ticketflow.payment.infrastructure.gateway;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.ticketflow.payment.application.port.out.PaymentIntentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Busca no Stripe o segredo que deixa o navegador confirmar a cobrança.
 *
 * <p>O {@code client_secret} é feito para viajar até o navegador — é assim que o
 * Stripe Elements funciona, e é o que permite o cartão ser digitado sem nunca
 * passar por este serviço. Mesmo assim ele não sai daqui para qualquer um: só
 * depois de o caso de uso confirmar que o pedido é de quem está pedindo.
 */
public class StripeIntentReader implements PaymentIntentReader {

    private static final Logger log = LoggerFactory.getLogger(StripeIntentReader.class);

    /**
     * Estados em que ainda há algo a confirmar.
     *
     * <p>Fora deles o segredo não serve para nada — cobrança paga, cancelada ou
     * em processamento no provedor — e devolvê-lo seria expor superfície de
     * graça.
     */
    private static final Set<String> CONFIRMABLE =
            Set.of("requires_payment_method", "requires_confirmation", "requires_action");

    private final StripeClient stripe;

    public StripeIntentReader(StripeClient stripe) {
        this.stripe = Objects.requireNonNull(stripe, "stripe client is required");
    }

    @Override
    public Optional<String> clientSecretFor(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) return Optional.empty();

        try {
            PaymentIntent intent = stripe.paymentIntents().retrieve(transactionId);
            if (!CONFIRMABLE.contains(intent.getStatus())) return Optional.empty();
            return Optional.ofNullable(intent.getClientSecret());
        } catch (StripeException e) {
            // Provedor fora do ar não é motivo para derrubar a consulta: a tela
            // mostra o pedido e informa que a confirmação está indisponível.
            // Estourar aqui trocaria "não dá para pagar agora" por "erro no site".
            log.warn("Nao foi possivel ler a intent {} no Stripe: {}", transactionId, e.getMessage());
            return Optional.empty();
        }
    }
}
