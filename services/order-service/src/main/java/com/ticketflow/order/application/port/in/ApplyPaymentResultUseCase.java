package com.ticketflow.order.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Driving port: finish an order using the payment outcome that arrived from Kafka.
 *
 * <p>This is the other half of the asynchronous flow. {@code CreateOrderUseCase}
 * leaves the order PENDING; this one resolves it, long after the customer's HTTP
 * request is gone.
 */
public interface ApplyPaymentResultUseCase {

    Result execute(Command command);

    /**
     * @param eventId       identity of the Kafka message; also the deduplication key
     * @param approved      true for PAGAMENTO_APROVADO, false for PAGAMENTO_RECUSADO
     * @param failureReason only meaningful when {@code approved} is false
     */
    record Command(UUID eventId,
                   UUID orderId,
                   boolean approved,
                   String failureReason,
                   Instant occurredAt) {

        public Command {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(orderId, "orderId is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
        }
    }

    enum Result {
        /** The order was moved to PAID or REJECTED. */
        APPLIED,
        /** This event had already been handled; nothing changed. */
        IGNORED_DUPLICATE,
        /**
         * Aprovação chegou para um pedido que já tinha acabado — cancelado ou
         * expirado. O pedido continua como está, e o dinheiro precisa voltar.
         *
         * <p>É o desfecho mais importante desta enumeração: ele significa que
         * existe cobrança sem pedido correspondente. Quem estorna é o Payment
         * Service, ao consumir o {@code ORDER_CANCELLED}; este valor existe para
         * o caso ser contável e visível em vez de virar exceção numa DLQ.
         */
        PAID_AFTER_CLOSE,
        /** Recusa chegou para um pedido que já tinha acabado. Nada a fazer. */
        IGNORED_CLOSED
    }
}
