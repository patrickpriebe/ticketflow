package com.ticketflow.payment.infrastructure.persistence.jpa;

import com.ticketflow.payment.infrastructure.persistence.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaProcessedEventRepository
        extends JpaRepository<ProcessedEventEntity, ProcessedEventEntity.Key> {

    boolean existsByEventIdAndConsumerGroup(UUID eventId, String consumerGroup);
}
