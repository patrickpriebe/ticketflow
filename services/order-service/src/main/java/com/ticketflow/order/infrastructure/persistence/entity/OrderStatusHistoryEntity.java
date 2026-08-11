package com.ticketflow.order.infrastructure.persistence.entity;

import com.ticketflow.order.domain.model.OrderStatus;
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

import java.time.Instant;
import java.util.UUID;

/** Maps {@code order_status_history} - append-only audit trail. */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private OrderStatus toStatus;

    @Column(length = 255)
    private String reason;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected OrderStatusHistoryEntity() {
    }

    public OrderStatusHistoryEntity(OrderStatus fromStatus,
                                    OrderStatus toStatus,
                                    String reason,
                                    UUID sourceEventId,
                                    Instant occurredAt) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.sourceEventId = sourceEventId;
        this.occurredAt = occurredAt;
    }

    void assignTo(OrderEntity order) {
        this.order = order;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
