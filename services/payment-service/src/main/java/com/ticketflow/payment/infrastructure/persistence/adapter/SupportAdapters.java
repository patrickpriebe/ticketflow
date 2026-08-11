package com.ticketflow.payment.infrastructure.persistence.adapter;

import com.ticketflow.payment.application.port.out.ProcessedEventRepository;
import com.ticketflow.payment.application.port.out.UnitOfWork;
import com.ticketflow.payment.infrastructure.persistence.entity.ProcessedEventEntity;
import com.ticketflow.payment.infrastructure.persistence.jpa.JpaProcessedEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

/** The two small driven adapters that support every use case. */
public final class SupportAdapters {

    private SupportAdapters() {
    }

    /**
     * Turns "run this atomically" into a Spring transaction, so no use case ever has
     * to import {@code @Transactional}.
     */
    @Component
    public static class SpringUnitOfWork implements UnitOfWork {

        private final TransactionTemplate transactionTemplate;

        public SpringUnitOfWork(PlatformTransactionManager transactionManager) {
            this.transactionTemplate = new TransactionTemplate(transactionManager);
        }

        @Override
        public <T> T execute(Supplier<T> work) {
            return transactionTemplate.execute(status -> work.get());
        }
    }

    /** The inbox. Consumer group and topic come from configuration, not the domain. */
    @Repository
    public static class ProcessedEventRepositoryAdapter implements ProcessedEventRepository {

        private final JpaProcessedEventRepository processedEvents;
        private final Clock clock;
        private final String consumerGroup;
        private final String topic;

        public ProcessedEventRepositoryAdapter(
                JpaProcessedEventRepository processedEvents,
                Clock clock,
                @Value("${spring.cloud.stream.bindings.orderCreated-in-0.group}") String consumerGroup,
                @Value("${spring.cloud.stream.bindings.orderCreated-in-0.destination}") String topic) {
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
}
