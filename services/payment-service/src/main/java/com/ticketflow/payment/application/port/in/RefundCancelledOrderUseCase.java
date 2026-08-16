package com.ticketflow.payment.application.port.in;

import com.ticketflow.payment.domain.model.Money;
import com.ticketflow.payment.domain.model.PaymentMethod;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Driving port: o pedido foi cancelado e a cobrança tem que acompanhar.
 *
 * <p>É a segunda metade do cancelamento. O Order Service cancela o pedido na
 * hora, sem perguntar nada a ninguém — perguntar seria a chamada síncrona entre
 * serviços que este projeto não admite, e prenderia o cliente numa tela pela
 * lentidão de um provedor. O preço disso é que o dinheiro pode já ter saído, e
 * quem resolve isso é este caso de uso.
 */
public interface RefundCancelledOrderUseCase {

    Result execute(Command command);

    /**
     * @param eventId       identidade da mensagem, e chave de deduplicação
     * @param amount        o total do pedido, que viaja no evento para o estorno
     *                      não precisar perguntar nada ao Order Service
     * @param paymentMethod só é usado quando não existe cobrança ainda, para
     *                      registrar a que nunca vai acontecer
     */
    record Command(UUID eventId,
                   UUID orderId,
                   UUID customerId,
                   Money amount,
                   PaymentMethod paymentMethod,
                   String reason,
                   Instant occurredAt) {

        public Command {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(orderId, "orderId is required");
            Objects.requireNonNull(customerId, "customerId is required");
            Objects.requireNonNull(amount, "amount is required");
            Objects.requireNonNull(paymentMethod, "paymentMethod is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
        }
    }

    enum Result {
        /** O dinheiro voltou. */
        REFUNDED,
        /** Havia cobrança, ainda não cobrada. Fechada sem chamar o provedor. */
        CANCELLED_BEFORE_CHARGE,
        /**
         * O cancelamento chegou antes do {@code ORDER_CREATED}. A cobrança foi
         * registrada já cancelada para nunca ser feita.
         */
        BLOCKED_BEFORE_CREATION,
        /** Não havia o que devolver: a cobrança tinha sido recusada. */
        NOTHING_TO_REFUND,
        /** Esta mensagem já tinha sido tratada. */
        IGNORED_DUPLICATE
    }
}
