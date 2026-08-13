package com.ticketflow.payment.infrastructure.persistence.adapter;

import com.ticketflow.payment.application.port.out.PaymentRepository;
import com.ticketflow.payment.domain.model.Money;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentAttempt;
import com.ticketflow.payment.infrastructure.persistence.entity.PaymentAttemptEntity;
import com.ticketflow.payment.infrastructure.persistence.entity.PaymentEntity;
import com.ticketflow.payment.infrastructure.persistence.jpa.JpaPaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final JpaPaymentRepository payments;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public PaymentRepositoryAdapter(JpaPaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByOrderId(UUID orderId) {
        return payments.findByOrderId(orderId).map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByGatewayTransaction(String gatewayName, String transactionId) {
        return payments.findByGatewayNameAndGatewayTransactionId(gatewayName, transactionId)
                .map(PaymentRepositoryAdapter::toDomain);
    }

    @Override
    public Payment save(Payment payment) {
        entityManager.persist(toEntity(payment));
        // Surfaces the UNIQUE on order_id now rather than at commit, so a duplicate
        // is a clear failure here instead of a mysterious 500 later.
        entityManager.flush();
        return payment;
    }

    @Override
    public Payment update(Payment payment) {
        PaymentEntity entity = entityManager.find(PaymentEntity.class, payment.id());
        if (entity == null) {
            throw new IllegalStateException("Payment %s disappeared".formatted(payment.id()));
        }

        entity.applyOutcome(payment.status(), payment.gatewayName(), payment.gatewayTransactionId(),
                payment.failureCode(), payment.failureReason(), payment.attempts(),
                payment.authorizedAt(), payment.updatedAt());

        for (PaymentAttempt attempt : payment.newAttempts()) {
            entity.addAttempt(new PaymentAttemptEntity(
                    attempt.attemptNumber(), attempt.outcome(), attempt.httpStatus(),
                    attempt.latencyMs(), attempt.requestPayload(), attempt.responsePayload(),
                    attempt.errorMessage(), clock.instant()));
        }
        return payment;
    }

    private PaymentEntity toEntity(Payment payment) {
        return new PaymentEntity(
                payment.id(), payment.orderId(), payment.customerId(),
                payment.amount().amount(), payment.amount().currency(),
                payment.method(), payment.status(), payment.attempts(),
                payment.createdAt(), payment.updatedAt());
    }

    private static Payment toDomain(PaymentEntity entity) {
        return Payment.restore(
                entity.getId(), entity.getOrderId(), entity.getCustomerId(),
                Money.of(entity.getAmount(), entity.getCurrency().trim()),
                entity.getMethod(), entity.getStatus(),
                entity.getGatewayName(), entity.getGatewayTransactionId(),
                entity.getFailureCode(), entity.getFailureReason(),
                entity.getAttempts(), entity.getAuthorizedAt(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }
}
