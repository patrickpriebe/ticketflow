package com.ticketflow.order.infrastructure.persistence.mapper;

import com.ticketflow.order.domain.model.Customer;
import com.ticketflow.order.domain.model.Money;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderItem;
import com.ticketflow.order.domain.model.OrderStatusChange;
import com.ticketflow.order.infrastructure.persistence.entity.OrderEntity;
import com.ticketflow.order.infrastructure.persistence.entity.OrderItemEntity;
import com.ticketflow.order.infrastructure.persistence.entity.OrderStatusHistoryEntity;

import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderEntity toEntity(Order order) {
        OrderEntity entity = new OrderEntity(
                order.id(),
                order.idempotencyKey(),
                order.customer().id(),
                order.customer().name(),
                order.customer().email(),
                order.ticketEventId(),
                order.status(),
                order.paymentMethod(),
                order.totalAmount().amount(),
                order.totalAmount().currency(),
                order.expiresAt(),
                order.createdAt(),
                order.updatedAt());

        order.items().forEach(item -> entity.addItem(new OrderItemEntity(
                item.id(),
                item.ticketCategoryId(),
                item.categoryName(),
                item.quantity(),
                item.unitPrice().amount(),
                item.subtotal().amount())));

        order.statusHistory().forEach(change -> entity.addStatusChange(new OrderStatusHistoryEntity(
                change.fromStatus(),
                change.toStatus(),
                change.reason(),
                change.sourceEventId(),
                change.occurredAt())));

        return entity;
    }

    public static Order toDomain(OrderEntity entity) {
        String currency = entity.getCurrency().trim();

        List<OrderItem> items = entity.getItems().stream()
                .map(item -> new OrderItem(
                        item.getId(),
                        item.getTicketCategoryId(),
                        item.getCategoryName(),
                        item.getQuantity(),
                        Money.of(item.getUnitPrice(), currency),
                        Money.of(item.getSubtotal(), currency)))
                .toList();

        List<OrderStatusChange> history = entity.getStatusHistory().stream()
                .map(change -> new OrderStatusChange(
                        change.getFromStatus(),
                        change.getToStatus(),
                        change.getReason(),
                        change.getSourceEventId(),
                        change.getOccurredAt()))
                .toList();

        return Order.restore(
                entity.getId(),
                entity.getIdempotencyKey(),
                new Customer(entity.getCustomerId(), entity.getCustomerName(), entity.getCustomerEmail()),
                entity.getTicketEventId(),
                entity.getStatus(),
                entity.getPaymentMethod(),
                items,
                Money.of(entity.getTotalAmount(), currency),
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                history,
                entity.getVersion());
    }
}
