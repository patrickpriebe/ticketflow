package com.ticketflow.payment.infrastructure.persistence.entity;

import com.ticketflow.payment.domain.model.PaymentMethod;
import com.ticketflow.payment.domain.model.PaymentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Maps {@code payments}. See V1__init_payment_schema.sql. */
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    private UUID id;

    /** UNIQUE in the schema - the last guard against charging one order twice. */
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    // CHAR(3) in the schema, not VARCHAR: without this the validator rejects the
    // entity at start-up.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "gateway_name", length = 60)
    private String gatewayName;

    @Column(name = "gateway_transaction_id", length = 120)
    private String gatewayTransactionId;

    @Column(name = "failure_code", length = 60)
    private String failureCode;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("attemptNumber ASC")
    private List<PaymentAttemptEntity> attemptsLog = new ArrayList<>();

    protected PaymentEntity() {
    }

    public PaymentEntity(UUID id, UUID orderId, UUID customerId, BigDecimal amount, String currency,
                         PaymentMethod method, PaymentStatus status, int attempts,
                         Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.status = status;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Applies the outcome decided by the domain. */
    public void applyOutcome(PaymentStatus status, String gatewayName, String gatewayTransactionId,
                             String failureCode, String failureReason, int attempts,
                             Instant authorizedAt, Instant updatedAt) {
        this.status = status;
        this.gatewayName = gatewayName;
        this.gatewayTransactionId = gatewayTransactionId;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.attempts = attempts;
        this.authorizedAt = authorizedAt;
        this.updatedAt = updatedAt;
    }

    public void addAttempt(PaymentAttemptEntity attempt) {
        attemptsLog.add(attempt);
        attempt.assignTo(this);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getGatewayName() {
        return gatewayName;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getAuthorizedAt() {
        return authorizedAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PaymentAttemptEntity> getAttemptsLog() {
        return attemptsLog;
    }
}
