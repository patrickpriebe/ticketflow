package com.ticketflow.payment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Mapeia {@code payment_webhook_events}. Ver V2__stripe_webhook_inbox.sql. */
@Entity
@Table(name = "payment_webhook_events")
public class WebhookEventEntity {

    @EmbeddedId
    private Id id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    protected WebhookEventEntity() {
    }

    public WebhookEventEntity(String provider, String eventId, String eventType, UUID paymentId) {
        this.id = new Id(provider, eventId);
        this.eventType = eventType;
        this.paymentId = paymentId;
    }

    public Id getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    /**
     * Chave composta: o provedor entra nela porque o mesmo identificador de evento
     * pode existir em dois provedores diferentes, e um dia haverá outro.
     */
    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "provider", nullable = false)
        private String provider;

        @Column(name = "event_id", nullable = false)
        private String eventId;

        protected Id() {
        }

        public Id(String provider, String eventId) {
            this.provider = provider;
            this.eventId = eventId;
        }

        public String getProvider() {
            return provider;
        }

        public String getEventId() {
            return eventId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Id that)) return false;
            return Objects.equals(provider, that.provider) && Objects.equals(eventId, that.eventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, eventId);
        }
    }
}
