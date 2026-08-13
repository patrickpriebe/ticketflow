package com.ticketflow.payment.infrastructure.persistence.jpa;

import com.ticketflow.payment.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaPaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    @EntityGraph(attributePaths = "attemptsLog")
    Optional<PaymentEntity> findByOrderId(UUID orderId);

    /**
     * O caminho do webhook: o provedor só conhece a transação que criamos com ele.
     *
     * <p>Coberto pelo índice único parcial {@code ux_payments_gateway_transaction},
     * então isto é uma busca por chave, não uma varredura.
     */
    @EntityGraph(attributePaths = "attemptsLog")
    Optional<PaymentEntity> findByGatewayNameAndGatewayTransactionId(String gatewayName,
                                                                    String gatewayTransactionId);
}
