package com.ticketflow.order.infrastructure.persistence.jpa;

import com.ticketflow.order.domain.model.OrderStatus;
import com.ticketflow.order.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaOrderRepository extends JpaRepository<OrderEntity, UUID> {

    // Only ONE collection is fetch-joined: Hibernate refuses to fetch two bags in a
    // single query (MultipleBagFetchException). statusHistory is loaded separately
    // via @BatchSize on the entity, which keeps a page of orders to a couple of
    // queries instead of one per order. Both happen inside the adapter's
    // transaction, so open-in-view can stay off.
    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    Page<OrderEntity> findByCustomerId(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Page<OrderEntity> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);
}
