package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.application.port.in.ListOrdersUseCase;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.domain.model.Order;

import java.util.Objects;

public class ListOrders implements ListOrdersUseCase {

    private final OrderRepository orders;

    public ListOrders(OrderRepository orders) {
        this.orders = Objects.requireNonNull(orders);
    }

    @Override
    public PageResult<Order> execute(Query query) {
        Objects.requireNonNull(query, "query is required");
        return orders.findByCustomer(query.customerId(), query.status(), query.pageQuery());
    }
}
