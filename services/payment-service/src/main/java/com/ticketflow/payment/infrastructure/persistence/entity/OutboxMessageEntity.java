package com.ticketflow.payment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps {@code outbox_messages}.
 *
 * <p>Same shape as the Order Service's, on purpose: each service owns its outbox and
 * neither can be made to redeploy because the other changed. The duplication is the
 * price of independence, and it is a price this project chooses to pay.
 */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessageEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 60)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, length = 120)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 120)
    private String partitionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String headers;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxMessageEntity() {
    }

    public OutboxMessageEntity(UUID id, String aggregateType, UUID aggregateId, String eventType,
                               String topic, String partitionKey, String payload, String headers,
                               Instant createdAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.headers = headers;
        this.status = "PENDING";
        this.attempts = 0;
        this.availableAt = createdAt;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant publishedAt) {
        this.status = "PUBLISHED";
        this.publishedAt = publishedAt;
        this.attempts++;
        this.lastError = null;
    }

    public void markFailed(String error, Instant retryAt) {
        this.attempts++;
        this.lastError = truncate(error);
        this.availableAt = retryAt;
    }

    public void giveUp(String error) {
        this.attempts++;
        this.lastError = truncate(error);
        this.status = "FAILED";
    }

    public boolean isPublished() {
        return "PUBLISHED".equals(status);
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getHeaders() {
        return headers;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
