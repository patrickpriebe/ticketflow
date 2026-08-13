package com.ticketflow.payment.application.port.out;

import com.ticketflow.payment.domain.model.Payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Optional<Payment> findByOrderId(UUID orderId);

    /**
     * Acha o pagamento pelo identificador que o provedor conhece.
     *
     * <p>É o caminho do webhook. O provedor não sabe o que é um pedido — ele
     * conhece o PaymentIntent que criamos, e é por ele que a resposta volta.
     */
    Optional<Payment> findByGatewayTransaction(String gatewayName, String transactionId);

    /** Inserts a new payment. The UNIQUE on order_id is the final guard against double charging. */
    Payment save(Payment payment);

    /** Persists the outcome and any new attempts recorded on the aggregate. */
    Payment update(Payment payment);
}
