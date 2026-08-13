package com.ticketflow.payment.domain.model;

import com.ticketflow.payment.domain.exception.InvalidPaymentStatusTransitionException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The aggregate root: the attempt to collect money for one order.
 *
 * <p>There is at most one payment per order - enforced by a UNIQUE constraint on
 * {@code payments.order_id}, which is the last line of defence against charging the
 * same customer twice when an ORDER_CREATED event is redelivered.
 *
 * <p>The class knows nothing about HTTP or Kafka. It is told what the gateway
 * answered and decides what that means.
 */
public class Payment {

    private final UUID id;
    private final UUID orderId;
    private final UUID customerId;
    private final Money amount;
    private final PaymentMethod method;
    private final Instant createdAt;
    private final long version;

    private PaymentStatus status;
    private String gatewayName;
    private String gatewayTransactionId;
    private String failureCode;
    private String failureReason;
    private int attempts;
    private Instant authorizedAt;
    private Instant updatedAt;

    private final List<PaymentAttempt> newAttempts = new ArrayList<>();

    private Payment(UUID id, UUID orderId, UUID customerId, Money amount, PaymentMethod method,
                    PaymentStatus status, int attempts, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "payment id is required");
        this.orderId = Objects.requireNonNull(orderId, "order id is required");
        this.customerId = Objects.requireNonNull(customerId, "customer id is required");
        this.amount = Objects.requireNonNull(amount, "amount is required");
        this.method = Objects.requireNonNull(method, "payment method is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.attempts = attempts;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        this.version = version;
    }

    /** A brand new payment, not yet sent anywhere. */
    public static Payment forOrder(UUID orderId, UUID customerId, Money amount,
                                   PaymentMethod method, Instant now) {
        return new Payment(UUID.randomUUID(), orderId, customerId, amount, method,
                PaymentStatus.PENDING, 0, now, now, 0L);
    }

    /** Rebuilds a stored payment. Applies no rules. */
    public static Payment restore(UUID id, UUID orderId, UUID customerId, Money amount,
                                  PaymentMethod method, PaymentStatus status,
                                  String gatewayName, String gatewayTransactionId,
                                  String failureCode, String failureReason,
                                  int attempts, Instant authorizedAt,
                                  Instant createdAt, Instant updatedAt, long version) {
        Payment payment = new Payment(id, orderId, customerId, amount, method, status,
                attempts, createdAt, updatedAt, version);
        payment.gatewayName = gatewayName;
        payment.gatewayTransactionId = gatewayTransactionId;
        payment.failureCode = failureCode;
        payment.failureReason = failureReason;
        payment.authorizedAt = authorizedAt;
        return payment;
    }

    /**
     * Records what the gateway said and moves the payment accordingly.
     *
     * @param attempt the call that was just made; kept for the audit trail
     */
    /**
     * The provider took the charge and will answer later, by webhook.
     *
     * <p>Deliberately not a status change: the payment stays PENDING, because
     * nothing was decided. What it does record is the provider's transaction id,
     * and that is the whole point — it is the only handle the webhook has to find
     * this payment again when the answer finally arrives.
     *
     * <p>Calling this twice is harmless: the second delivery of the same message
     * finds the payment already carrying the id and only adds an attempt.
     */
    public void awaitProviderConfirmation(PaymentAttempt attempt, String gatewayName,
                                          String transactionId, Instant now) {
        Objects.requireNonNull(attempt, "attempt is required");
        Objects.requireNonNull(now, "now is required");

        if (transactionId == null || transactionId.isBlank()) {
            // Without it the webhook cannot match the event back to this payment,
            // and the money would move with nobody updating the order.
            throw new IllegalArgumentException(
                    "A payment awaiting confirmation must carry the provider's transaction id");
        }
        if (status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStatusTransitionException(status, PaymentStatus.PENDING);
        }

        this.newAttempts.add(attempt);
        this.attempts++;
        this.gatewayName = gatewayName;
        this.gatewayTransactionId = transactionId;
        this.updatedAt = now;
    }

    public void applyGatewayOutcome(PaymentAttempt attempt, String gatewayName,
                                    String transactionId, String failureCode,
                                    String failureReason, Instant now) {
        Objects.requireNonNull(attempt, "attempt is required");
        Objects.requireNonNull(now, "now is required");

        PaymentStatus target = switch (attempt.outcome()) {
            case APPROVED -> PaymentStatus.APPROVED;
            case REJECTED -> PaymentStatus.REJECTED;
            // No usable answer: the payment is not refused, it is unresolved.
            case TIMEOUT, ERROR -> PaymentStatus.FAILED;
            // ACCEPTED is not an outcome that settles anything, so it must not
            // reach here. It goes through awaitProviderConfirmation instead.
            case ACCEPTED -> throw new IllegalArgumentException(
                    "ACCEPTED does not settle a payment; use awaitProviderConfirmation");
        };

        if (!status.canTransitionTo(target)) {
            throw new InvalidPaymentStatusTransitionException(status, target);
        }

        if (target == PaymentStatus.APPROVED && (transactionId == null || transactionId.isBlank())) {
            // The schema enforces this too; catching it here gives a useful message
            // instead of a constraint violation at flush time.
            throw new IllegalArgumentException("An approved payment must carry a gateway transaction id");
        }

        this.newAttempts.add(attempt);
        this.attempts++;
        this.status = target;
        this.gatewayName = gatewayName;
        this.updatedAt = now;

        if (target == PaymentStatus.APPROVED) {
            this.gatewayTransactionId = transactionId;
            this.authorizedAt = now;
            this.failureCode = null;
            this.failureReason = null;
        } else {
            this.failureCode = failureCode;
            this.failureReason = failureReason;
        }
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }

    public boolean isSettled() {
        return status.isFinal();
    }

    /** Attempts recorded in this session, not yet persisted. */
    public List<PaymentAttempt> newAttempts() {
        return List.copyOf(newAttempts);
    }

    public int nextAttemptNumber() {
        return attempts + 1;
    }

    public UUID id() {
        return id;
    }

    public UUID orderId() {
        return orderId;
    }

    public UUID customerId() {
        return customerId;
    }

    public Money amount() {
        return amount;
    }

    public PaymentMethod method() {
        return method;
    }

    public PaymentStatus status() {
        return status;
    }

    public String gatewayName() {
        return gatewayName;
    }

    public String gatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String failureCode() {
        return failureCode;
    }

    public String failureReason() {
        return failureReason;
    }

    public int attempts() {
        return attempts;
    }

    public Instant authorizedAt() {
        return authorizedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
