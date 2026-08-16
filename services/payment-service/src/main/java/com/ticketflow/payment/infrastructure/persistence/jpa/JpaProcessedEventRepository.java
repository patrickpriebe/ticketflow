package com.ticketflow.payment.infrastructure.persistence.jpa;

import com.ticketflow.payment.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface JpaProcessedEventRepository
        extends JpaRepository<ProcessedEventEntity, ProcessedEventEntity.Key> {

    boolean existsByEventIdAndConsumerGroup(UUID eventId, String consumerGroup);

    /**
     * Apaga registros de inbox mais velhos que a janela de retenção.
     *
     * <p>Esta tabela é o que impede uma mensagem reentregue de virar uma segunda
     * cobrança. A janela precisa ser <strong>maior que a retenção do tópico</strong>:
     * enquanto o Kafka ainda puder reentregar o {@code ORDER_CREATED}, o registro
     * que deduplica tem que existir. Os tópicos guardam 7 dias; o padrão daqui é 30.
     */
    @Modifying
    @Query(value = """
            delete from processed_events
            where ctid in (
                select ctid from processed_events
                where processed_at < :threshold
                limit :batchSize
            )
            """, nativeQuery = true)
    int deleteProcessedBefore(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);
}
