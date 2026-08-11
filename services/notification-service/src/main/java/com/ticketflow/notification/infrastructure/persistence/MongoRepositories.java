package com.ticketflow.notification.infrastructure.persistence;

import com.ticketflow.notification.application.port.out.Repositories;
import com.ticketflow.notification.domain.model.Notification;
import com.ticketflow.notification.domain.model.OrderSnapshot;
import com.ticketflow.notification.domain.model.Ticket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The MongoDB side of the driven ports.
 *
 * <p>Everything goes through {@code save}, which upserts by {@code _id}. Combined
 * with the deterministic identifiers the domain produces, that is what gives this
 * service idempotence without transactions - MongoDB standalone has no
 * multi-document transactions, and pretending otherwise would be a lie the first
 * time a message was redelivered.
 */
public final class MongoRepositories {

    static final String TICKETS = "tickets";
    static final String NOTIFICATIONS = "notifications";
    static final String ORDER_SNAPSHOTS = "order_snapshots";
    static final String PROCESSED_EVENTS = "processed_events";

    private MongoRepositories() {
    }

    @Repository
    public static class MongoOrderSnapshots implements Repositories.OrderSnapshots {

        private final MongoTemplate mongo;

        public MongoOrderSnapshots(MongoTemplate mongo) {
            this.mongo = mongo;
        }

        @Override
        public void save(OrderSnapshot snapshot) {
            // _id is the orderId, so a redelivered ORDER_CREATED overwrites rather
            // than accumulating snapshots.
            mongo.save(snapshot, ORDER_SNAPSHOTS);
        }

        @Override
        public Optional<OrderSnapshot> findByOrderId(String orderId) {
            return Optional.ofNullable(mongo.findOne(
                    Query.query(Criteria.where("_id").is(orderId)),
                    OrderSnapshot.class, ORDER_SNAPSHOTS));
        }
    }

    @Repository
    public static class MongoTickets implements Repositories.Tickets {

        private final MongoTemplate mongo;

        public MongoTickets(MongoTemplate mongo) {
            this.mongo = mongo;
        }

        @Override
        public void saveAll(List<Ticket> tickets) {
            tickets.forEach(ticket -> mongo.save(ticket, TICKETS));
        }

        @Override
        public List<Ticket> findByOrderId(String orderId) {
            return mongo.find(Query.query(Criteria.where("orderId").is(orderId)),
                    Ticket.class, TICKETS);
        }
    }

    @Repository
    public static class MongoNotifications implements Repositories.Notifications {

        private final MongoTemplate mongo;

        public MongoNotifications(MongoTemplate mongo) {
            this.mongo = mongo;
        }

        @Override
        public void save(Notification notification) {
            mongo.save(notification, NOTIFICATIONS);
        }
    }

    @Repository
    public static class MongoProcessedEvents implements Repositories.ProcessedEvents {

        private final MongoTemplate mongo;
        private final Clock clock;
        private final String consumerGroup;
        private final String topic;

        public MongoProcessedEvents(
                MongoTemplate mongo,
                Clock clock,
                @Value("${spring.cloud.stream.bindings.paymentProcessed-in-0.group}") String consumerGroup,
                @Value("${spring.cloud.stream.bindings.paymentProcessed-in-0.destination}") String topic) {
            this.mongo = mongo;
            this.clock = clock;
            this.consumerGroup = consumerGroup;
            this.topic = topic;
        }

        @Override
        public boolean alreadyProcessed(String eventId) {
            return mongo.exists(Query.query(
                            Criteria.where("eventId").is(eventId)
                                    .and("consumerGroup").is(consumerGroup)),
                    PROCESSED_EVENTS);
        }

        @Override
        public void record(String eventId) {
            try {
                mongo.insert(Map.of(
                        "eventId", eventId,
                        "consumerGroup", consumerGroup,
                        "topic", topic,
                        "processedAt", java.util.Date.from(clock.instant())), PROCESSED_EVENTS);
            } catch (DuplicateKeyException e) {
                // Another delivery of the same event got here first. That is the
                // unique index doing its job, not a failure.
            }
        }
    }
}
