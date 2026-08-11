package com.ticketflow.order.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps {@code ticket_categories}.
 *
 * <p>{@link Version} is what stops two concurrent orders from selling the same last
 * ticket: both read version N, both write, the second gets an optimistic lock
 * failure and is rejected.
 */
@Entity
@Table(name = "ticket_categories")
public class TicketCategoryEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private TicketEventEntity ticketEvent;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_amount", nullable = false)
    private BigDecimal priceAmount;

    // The column is CHAR(3), not VARCHAR: without this the schema validator
    // rejects the entity at start-up ("found bpchar, expecting varchar").
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected TicketCategoryEntity() {
    }

    public void applyCounters(int reservedQuantity, int soldQuantity) {
        this.reservedQuantity = reservedQuantity;
        this.soldQuantity = soldQuantity;
    }

    public UUID getId() {
        return id;
    }

    public TicketEventEntity getTicketEvent() {
        return ticketEvent;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public int getSoldQuantity() {
        return soldQuantity;
    }

    public long getVersion() {
        return version;
    }
}
