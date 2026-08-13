package com.ticketflow.payment.application.port.in;

import com.ticketflow.payment.domain.model.AttemptOutcome;

import java.util.Objects;

/**
 * Driving port: o provedor avisou como terminou uma cobrança que estava em aberto.
 *
 * <p>É o outro lado do {@link ProcessOrderPaymentUseCase}. Aquele começa a
 * cobrança; este a encerra, quando a resposta não veio na mesma chamada — o caso
 * do boleto, que é pago num banco dias depois.
 *
 * <p>O caso de uso não sabe o que é um webhook, nem HTTP, nem assinatura. Recebe
 * o fato já verificado: "o provedor X diz que a transação Y foi aprovada". Quem
 * checa se a mensagem é legítima é a camada de infraestrutura, antes de chegar
 * aqui.
 */
public interface SettlePaymentFromProviderUseCase {

    Result execute(Command command);

    /**
     * @param providerEventId identificador do evento no provedor. É a chave de
     *                        idempotência: o provedor reenvia até receber 200.
     * @param outcome         apenas {@code APPROVED} ou {@code REJECTED} — um
     *                        webhook é sempre uma resposta definitiva.
     */
    record Command(String provider,
                   String providerEventId,
                   String eventType,
                   String transactionId,
                   AttemptOutcome outcome,
                   String failureCode,
                   String failureReason,
                   String rawPayloadSummary) {

        public Command {
            Objects.requireNonNull(provider, "provider is required");
            Objects.requireNonNull(providerEventId, "providerEventId is required");
            Objects.requireNonNull(transactionId, "transactionId is required");
            Objects.requireNonNull(outcome, "outcome is required");
            if (!outcome.isFinal()) {
                throw new IllegalArgumentException(
                        "Um webhook so encerra com resposta definitiva, veio " + outcome);
            }
        }
    }

    enum Result {
        /** O pagamento passou a APPROVED e o evento foi publicado. */
        APPROVED,
        /** O pagamento passou a REJECTED e o evento foi publicado. */
        REJECTED,
        /** Este mesmo webhook já tinha sido processado. */
        IGNORED_DUPLICATE,
        /**
         * O pagamento já tinha resposta final — tipicamente cartão, que resolve na
         * própria chamada e ainda assim gera webhook. Nada a fazer, e nada a
         * publicar de novo.
         */
        ALREADY_SETTLED,
        /**
         * Nenhum pagamento nosso corresponde a essa transação. Não é erro: a conta
         * do provedor pode ser usada por outra coisa, e um 200 evita que ele fique
         * reenviando para sempre.
         */
        UNKNOWN_TRANSACTION
    }
}
