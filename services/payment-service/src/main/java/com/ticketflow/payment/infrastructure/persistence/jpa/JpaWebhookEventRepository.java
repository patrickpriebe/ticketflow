package com.ticketflow.payment.infrastructure.persistence.jpa;

import com.ticketflow.payment.infrastructure.persistence.entity.WebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Um arquivo por interface de repositório Spring Data.
 *
 * <p>Agrupar várias como interfaces aninhadas dentro de uma classe faz o scan não
 * encontrá-las, e o erro só aparece no boot como "No qualifying bean" — sintoma
 * que não aponta para a causa.
 */
public interface JpaWebhookEventRepository
        extends JpaRepository<WebhookEventEntity, WebhookEventEntity.Id> {
}
