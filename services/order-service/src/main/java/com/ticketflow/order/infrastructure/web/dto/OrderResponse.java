package com.ticketflow.order.infrastructure.web.dto;

import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderItem;
import com.ticketflow.order.domain.model.OrderStatusChange;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response body for the order endpoints.
 *
 * <p>{@code status} is {@code PENDING} on creation and only becomes final later, so
 * a client must poll this resource rather than expect the outcome from the POST.
 */
public record OrderResponse(UUID id,
                            String status,
                            CustomerPayload customer,
                            String paymentMethod,
                            List<Item> items,
                            MoneyResponse totalAmount,
                            Instant expiresAt,
                            List<StatusChange> statusHistory,
                            Instant createdAt,
                            Instant updatedAt) {

    public record CustomerPayload(UUID id, String name, String email) {
    }

    public record Item(UUID ticketCategoryId,
                       String categoryName,
                       int quantity,
                       MoneyResponse unitPrice,
                       MoneyResponse subtotal) {

        static Item from(OrderItem item) {
            return new Item(
                    item.ticketCategoryId(),
                    item.categoryName(),
                    item.quantity(),
                    MoneyResponse.from(item.unitPrice()),
                    MoneyResponse.from(item.subtotal()));
        }
    }

    public record StatusChange(String fromStatus, String toStatus, String reason, Instant occurredAt) {

        static StatusChange from(OrderStatusChange change) {
            return new StatusChange(
                    change.fromStatus() == null ? null : change.fromStatus().name(),
                    change.toStatus().name(),
                    change.reason(),
                    change.occurredAt());
        }
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.status().name(),
                new CustomerPayload(order.customer().id(), order.customer().name(), order.customer().email()),
                order.paymentMethod().name(),
                order.items().stream().map(Item::from).toList(),
                MoneyResponse.from(order.totalAmount()),
                order.expiresAt(),
                order.statusHistory().stream().map(StatusChange::from).toList(),
                order.createdAt(),
                order.updatedAt());
    }
}
