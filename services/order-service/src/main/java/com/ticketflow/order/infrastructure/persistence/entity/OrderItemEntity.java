package com.ticketflow.order.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Maps {@code order_items}. Prices are copies, frozen at the moment of purchase. */
@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "ticket_category_id", nullable = false)
    private UUID ticketCategoryId;

    @Column(name = "category_name", nullable = false, length = 80)
    private String categoryName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal subtotal;

    protected OrderItemEntity() {
    }

    public OrderItemEntity(UUID id,
                           UUID ticketCategoryId,
                           String categoryName,
                           int quantity,
                           BigDecimal unitPrice,
                           BigDecimal subtotal) {
        this.id = id;
        this.ticketCategoryId = ticketCategoryId;
        this.categoryName = categoryName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    void assignTo(OrderEntity order) {
        this.order = order;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketCategoryId() {
        return ticketCategoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
