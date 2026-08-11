package com.ticketflow.order.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps {@code processed_events} - the inbox.
 *
 * <p>The primary key is (event_id, consumer_group): the same event may legitimately
 * be processed once by each consumer group, but never twice by the same one.
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventEntity.Key.class)
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Id
    @Column(name = "consumer_group", nullable = false, length = 120)
    private String consumerGroup;

    @Column(nullable = false, length = 120)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
    }

    public ProcessedEventEntity(UUID eventId, String consumerGroup, String topic, Instant processedAt) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
        this.topic = topic;
        this.processedAt = processedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    /** Composite key holder required by {@link IdClass}. */
    public static class Key implements Serializable {

        private UUID eventId;
        private String consumerGroup;

        public Key() {
        }

        public Key(UUID eventId, String consumerGroup) {
            this.eventId = eventId;
            this.consumerGroup = consumerGroup;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(eventId, key.eventId)
                    && Objects.equals(consumerGroup, key.consumerGroup);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumerGroup);
        }
    }
}
