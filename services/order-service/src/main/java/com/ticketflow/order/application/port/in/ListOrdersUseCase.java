package com.ticketflow.order.application.port.in;

import com.ticketflow.order.application.pagination.PageQuery;
import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderStatus;

import java.util.Objects;
import java.util.UUID;

/** Driving port: a customer's order history. */
public interface ListOrdersUseCase {

    PageResult<Order> execute(Query query);

    /** @param status optional filter; null means every status */
    record Query(UUID customerId, OrderStatus status, PageQuery pageQuery) {

        public Query {
            Objects.requireNonNull(customerId, "customer id is required");
            Objects.requireNonNull(pageQuery, "page query is required");
        }
    }
}
