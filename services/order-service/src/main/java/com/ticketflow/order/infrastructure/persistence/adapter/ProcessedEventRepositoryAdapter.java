package com.ticketflow.order.infrastructure.persistence.adapter;

import com.ticketflow.order.application.port.out.ProcessedEventRepository;
import com.ticketflow.order.infrastructure.persistence.entity.ProcessedEventEntity;
import com.ticketflow.order.infrastructure.persistence.jpa.JpaProcessedEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.UUID;

/**
 * Driven adapter for the inbox.
 *
 * <p>The consumer group and topic come from configuration rather than from the
 * application layer, which has no business knowing what a consumer group is.
 */
@Repository
public class ProcessedEventRepositoryAdapter implements ProcessedEventRepository {

    private final JpaProcessedEventRepository processedEvents;
    private final Clock clock;
    private final String consumerGroup;
    private final String topic;

    public ProcessedEventRepositoryAdapter(
            JpaProcessedEventRepository processedEvents,
            Clock clock,
            @Value("${spring.cloud.stream.bindings.paymentProcessed-in-0.group}") String consumerGroup,
            @Value("${spring.cloud.stream.bindings.paymentProcessed-in-0.destination}") String topic) {
        this.processedEvents = processedEvents;
        this.clock = clock;
        this.consumerGroup = consumerGroup;
        this.topic = topic;
    }

    @Override
    public boolean alreadyProcessed(UUID eventId) {
        return processedEvents.existsByEventIdAndConsumerGroup(eventId, consumerGroup);
    }

    @Override
    public void record(UUID eventId) {
        processedEvents.save(new ProcessedEventEntity(eventId, consumerGroup, topic, clock.instant()));
    }
}
