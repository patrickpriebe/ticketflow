package com.ticketflow.order.infrastructure.persistence.jpa;

import com.ticketflow.order.infrastructure.persistence.entity.OutboxMessageEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Quando a mensagem pendente mais antiga foi escrita.
     *
     * <p>Diz mais que a contagem. Cinquenta mensagens de dois segundos é um pico
     * de tráfego; uma só, parada há dez minutos, é incidente — e as duas
     * situações têm exatamente a mesma cara num gráfico de quantidade.
     */
    @Query("select min(m.createdAt) from OutboxMessageEntity m where m.status = 'PENDING'")
    Instant oldestPendingCreatedAt();

    /**
     * Apaga mensagens já publicadas há mais tempo que a janela de retenção.
     *
     * <p>Só {@code PUBLISHED}. {@code PENDING} ainda tem que sair, e {@code FAILED}
     * é justamente o que alguém precisa olhar — apagar qualquer um dos dois
     * transformaria a limpeza em perda de evento.
     *
     * <p>Em lote, e não num {@code delete} da tabela inteira: uma varredura que
     * apaga meses de uma vez segura lock e transação por um tempo imprevisível,
     * e a instância gerenciada onde isto roda tem cinco conexões no pool.
     */
    @Modifying
    @Query(value = """
            delete from outbox_messages
            where id in (
                select id from outbox_messages
                where status = 'PUBLISHED' and published_at < :threshold
                limit :batchSize
            )
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);
}
