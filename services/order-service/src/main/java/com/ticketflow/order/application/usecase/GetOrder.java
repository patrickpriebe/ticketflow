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
    public Order execute(UUID orderId) {
        Objects.requireNonNull(orderId, "order id is required");
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
