package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.port.in.GetOrderUseCase;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.domain.exception.OrderNotFoundException;
import com.ticketflow.order.domain.model.Order;

import java.util.Objects;
import java.util.UUID;

public class GetOrder implements GetOrderUseCase {

    private final OrderRepository orders;

    public GetOrder(OrderRepository orders) {
        this.orders = Objects.requireNonNull(orders);
    }

    @Override
    public Order execute(UUID orderId, UUID requesterId) {
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(requesterId, "requester id is required");

        return orders.findById(orderId)
                .filter(order -> order.customer().id().equals(requesterId))
                // Mesmo erro para "não existe" e "não é seu", de propósito: um 403
                // aqui confirmaria a existência do pedido para quem está sondando.
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
