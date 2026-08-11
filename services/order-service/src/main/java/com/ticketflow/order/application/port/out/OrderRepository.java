package com.ticketflow.order.application.port.out;

import com.ticketflow.order.application.pagination.PageQuery;
import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.domain.exception.DuplicateIdempotencyKeyException;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Driven port: how the application stores and reads orders.
 *
 * <p>Declared here, implemented in {@code infrastructure.persistence}. The interface
 * mentions only domain types, so a use case never learns that JPA exists.
 */
public interface OrderRepository {

    /**
     * Inserts a brand new order.
     *
     * @throws DuplicateIdempotencyKeyException if the idempotency key is already taken
     */
    Order save(Order order);

    /**
     * Persists changes to an order that already exists - a status transition and the
     * history entry that goes with it.
     *
     * @throws com.ticketflow.order.domain.exception.ConcurrentOrderUpdateException
     *         if another transaction changed the same order first
     */
    Order update(Order order);

    Optional<Order> findById(UUID orderId);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    PageResult<Order> findByCustomer(UUID customerId, OrderStatus status, PageQuery pageQuery);
}
