package com.ticketflow.order.infrastructure.persistence.jpa;

import com.ticketflow.order.infrastructure.persistence.entity.OutboxMessageEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaOutboxRepository extends JpaRepository<OutboxMessageEntity, UUID> {

    /**
     * Claims a batch of messages waiting to be published.
     *
     * <p>{@code PESSIMISTIC_WRITE} with a lock timeout of {@code -2} is Hibernate's
     * spelling of {@code FOR UPDATE SKIP LOCKED}. That is what makes the relay safe
     * to run on several instances at once: each one takes rows nobody else holds
     * instead of blocking, so no message is published twice by two pods and no
     * instance sits waiting on another's lock.
     *
     * <p>The {@code status = 'PENDING'} predicate matches the partial index
     * {@code ix_outbox_dispatchable}, so the scan stays small as the table grows.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select m from OutboxMessageEntity m
            where m.status = 'PENDING' and m.availableAt <= :now
            order by m.createdAt asc
            """)
    List<OutboxMessageEntity> findDispatchable(@Param("now") Instant now, Pageable pageable);

    List<OutboxMessageEntity> findByAggregateIdOrderByCreatedAtAsc(UUID aggregateId);

    long countByStatus(String status);
}
