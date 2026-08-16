package com.ticketflow.payment.infrastructure.persistence.jpa;

import com.ticketflow.payment.infrastructure.persistence.entity.WebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * Um arquivo por interface de repositório Spring Data.
 *
 * <p>Agrupar várias como interfaces aninhadas dentro de uma classe faz o scan não
 * encontrá-las, e o erro só aparece no boot como "No qualifying bean" — sintoma
 * que não aponta para a causa.
 */
public interface JpaWebhookEventRepository
        extends JpaRepository<WebhookEventEntity, WebhookEventEntity.Id> {

    /**
     * Apaga eventos de webhook mais velhos que a janela de retenção.
     *
     * <p>Esta é a terceira tabela que só crescia, e a que tem o risco mais direto:
     * é ela que impede o mesmo {@code payment_intent.succeeded} de ser aplicado
     * duas vezes quando o Stripe reenvia. O provedor reenvia por até alguns dias
     * enquanto não receber 200, então a janela tem que cobrir isso com folga.
     */
    @Modifying
    @Query(value = """
            delete from payment_webhook_events
            where ctid in (
                select ctid from payment_webhook_events
                where received_at < :threshold
                limit :batchSize
            )
            """, nativeQuery = true)
    int deleteReceivedBefore(@Param("threshold") Instant threshold, @Param("batchSize") int batchSize);
}
