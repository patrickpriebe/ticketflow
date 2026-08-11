package com.ticketflow.payment.infrastructure.persistence.entity;

import com.ticketflow.payment.domain.model.AttemptOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Maps {@code payment_attempts} - one row per call to the gateway.
 *
 * <p>This table is what makes the Wiremock scenarios verifiable in production terms:
 * a timeout leaves a row with outcome TIMEOUT, so "the provider was flaky last
 * Tuesday" is a query rather than a guess.
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentEntity payment;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptOutcome outcome;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    /** Masked request only - never a card number, CVV or expiry date. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload")
    private String responsePayload;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentAttemptEntity() {
    }

    public PaymentAttemptEntity(int attemptNumber, AttemptOutcome outcome, Integer httpStatus,
                                Integer latencyMs, String requestPayload, String responsePayload,
                                String errorMessage, Instant createdAt) {
        this.attemptNumber = attemptNumber;
        this.outcome = outcome;
        this.httpStatus = httpStatus;
        this.latencyMs = latencyMs;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    void assignTo(PaymentEntity payment) {
        this.payment = payment;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public AttemptOutcome getOutcome() {
        return outcome;
    }
}
