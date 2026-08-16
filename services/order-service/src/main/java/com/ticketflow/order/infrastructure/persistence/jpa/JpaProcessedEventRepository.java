package com.ticketflow.order.infrastructure.persistence.jpa;

import com.ticketflow.order.infrastructure.persistence.entity.ProcessedEventEntity;
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
     * <p>Esta tabela é o que impede uma mensagem reentregue de ser processada
     * duas vezes, então apagar cedo demais reabre exatamente o defeito que ela
     * existe para fechar. A janela precisa ser <strong>maior que a retenção do
     * tópico</strong>: enquanto o Kafka ainda puder reentregar a mensagem, o
     * registro dela tem que estar aqui. Os tópicos guardam 7 dias; o padrão
     * daqui é 30.
     *
     * <p>Em lote, pelo mesmo motivo do outbox — e usando o índice
     * {@code ix_processed_events_processed_at}, que já existia sem uso.
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
