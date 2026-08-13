package com.ticketflow.payment.infrastructure.persistence.adapter;

import com.ticketflow.payment.application.port.out.WebhookEventRepository;
import com.ticketflow.payment.infrastructure.persistence.entity.WebhookEventEntity;
import com.ticketflow.payment.infrastructure.persistence.jpa.JpaWebhookEventRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class WebhookEventRepositoryAdapter implements WebhookEventRepository {

    private final JpaWebhookEventRepository events;

    public WebhookEventRepositoryAdapter(JpaWebhookEventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public boolean alreadyHandled(String provider, String eventId) {
        return events.existsById(new WebhookEventEntity.Id(provider, eventId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String provider, String eventId, String eventType, UUID paymentId) {
        // MANDATORY de propósito: esta linha só pode existir na mesma transação que
        // liquidou o pagamento e escreveu o evento no outbox. Gravada sozinha, o
        // webhook ficaria marcado como tratado sem nada ter sido publicado — e o
        // provedor não teria motivo para reenviar.
        events.save(new WebhookEventEntity(provider, eventId, eventType, paymentId));
    }
}
