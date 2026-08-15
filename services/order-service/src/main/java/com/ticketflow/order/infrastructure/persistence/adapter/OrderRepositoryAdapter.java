package com.ticketflow.order.infrastructure.persistence.adapter;

import com.ticketflow.order.application.pagination.PageQuery;
import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.domain.exception.ConcurrentOrderUpdateException;
import com.ticketflow.order.domain.exception.DuplicateIdempotencyKeyException;
import com.ticketflow.order.domain.exception.OrderNotFoundException;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderStatus;
import com.ticketflow.order.domain.model.OrderStatusChange;
import com.ticketflow.order.infrastructure.persistence.entity.OrderEntity;
import com.ticketflow.order.infrastructure.persistence.entity.OrderStatusHistoryEntity;
import com.ticketflow.order.infrastructure.persistence.jpa.JpaOrderRepository;
import com.ticketflow.order.infrastructure.persistence.mapper.OrderMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven adapter: the JPA side of {@link OrderRepository}.
 *
 * <p>All the Spring and JPA knowledge the application needs lives here, so the use
 * cases can stay plain Java.
 */
@Repository
public class OrderRepositoryAdapter implements OrderRepository {

    private final JpaOrderRepository jpaOrders;

    @PersistenceContext
    private EntityManager entityManager;

    public OrderRepositoryAdapter(JpaOrderRepository jpaOrders) {
        this.jpaOrders = jpaOrders;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = OrderMapper.toEntity(order);
        entityManager.persist(entity);
        try {
            // Flushing here is what makes the idempotency contract work. Without it
            // the unique-key violation would only surface at commit time, outside
            // this try block, and would reach the client as a 500 instead of being
            // translated into "you already placed this order".
            entityManager.flush();
        } catch (RuntimeException e) {
            if (violates(e, "uq_orders_idempotency_key")) {
                throw new DuplicateIdempotencyKeyException(order.idempotencyKey());
            }
            throw e;
        }
        return order;
    }

    @Override
    public Order update(Order order) {
        OrderEntity entity = entityManager.find(OrderEntity.class, order.id());
        if (entity == null) {
            throw new OrderNotFoundException(order.id());
        }

        // Explicit optimistic check: the domain object was read in an earlier
        // transaction, so if the stored version moved on since then, someone else
        // changed this order and our decision was made on stale state.
        if (entity.getVersion() != order.version()) {
            throw new ConcurrentOrderUpdateException(order.id());
        }

        entity.applyStatus(order.status(), order.updatedAt());
        // Only the newest transitions are appended - the trail is append-only, so
        // rows already stored are never rewritten.
        for (int i = entity.getStatusHistory().size(); i < order.statusHistory().size(); i++) {
            OrderStatusChange change = order.statusHistory().get(i);
            entity.addStatusChange(new OrderStatusHistoryEntity(
                    change.fromStatus(), change.toStatus(), change.reason(),
                    change.sourceEventId(), change.occurredAt()));
        }

        try {
            // Surfaces the optimistic-lock conflict here rather than at commit, so
            // it can be translated into a domain failure the consumer can retry.
            entityManager.flush();
        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            throw new ConcurrentOrderUpdateException(order.id());
        }
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID orderId) {
        return jpaOrders.findWithDetailsById(orderId).map(OrderMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByIdempotencyKey(UUID customerId, String idempotencyKey) {
        return jpaOrders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(OrderMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> findByCustomer(UUID customerId, OrderStatus status, PageQuery pageQuery) {
        PageRequest pageRequest = PageRequest.of(pageQuery.page(), pageQuery.size());
        Page<OrderEntity> page = (status == null)
                ? jpaOrders.findByCustomerId(customerId, pageRequest)
                : jpaOrders.findByCustomerIdAndStatus(customerId, status, pageRequest);

        return new PageResult<>(
                page.getContent().stream().map(OrderMapper::toDomain).toList(),
                pageQuery.page(),
                pageQuery.size(),
                page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findExpired(Instant now, int limit) {
        return jpaOrders
                .findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        OrderStatus.PENDING, now, PageRequest.of(0, limit))
                .stream()
                .map(OrderMapper::toDomain)
                .toList();
    }

    /** Walks the cause chain looking for a specific database constraint by name. */
    private static boolean violates(Throwable error, String constraintName) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase().contains(constraintName)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
