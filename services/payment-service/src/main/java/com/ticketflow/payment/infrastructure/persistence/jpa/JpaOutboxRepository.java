package com.ticketflow.payment.infrastructure.persistence.jpa;

import com.ticketflow.payment.infrastructure.persistence.entity.OutboxMessageEntity;
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
     * Claims a batch with {@code FOR UPDATE SKIP LOCKED} - a lock timeout of -2 is
     * Hibernate's spelling of SKIP LOCKED. Several instances of this service can then
     * run the relay at once without publishing the same row twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select m from OutboxMessageEntity m
            where m.status = 'PENDING' and m.availableAt <= :now
            order by m.createdAt asc
            """)
    List<OutboxMessageEntity> findDispatchable(@Param("now") Instant now, Pageable pageable);

    long countByStatus(String status);

    /**
     * Quando a mensagem pendente mais antiga foi escrita.
     *
     * <p>Diz mais que a contagem. Cinquenta mensagens de dois segundos é um pico
     * de tráfego; uma só, parada há dez minutos, é incidente — e as duas
     * situações têm exatamente a mesma cara num gráfico de quantidade.
     */
    @Query("select min(m.createdAt) from OutboxMessageEntity m where m.status = 'PENDING'")
    Instant oldestPendingCreatedAt();
}
