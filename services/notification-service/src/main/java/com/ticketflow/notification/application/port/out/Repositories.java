package com.ticketflow.notification.application.port.out;

import com.ticketflow.notification.domain.model.Notification;
import com.ticketflow.notification.domain.model.OrderSnapshot;
import com.ticketflow.notification.domain.model.Ticket;

import java.util.List;
import java.util.Optional;

/** The driven ports. Each is tiny, so they are declared together. */
public final class Repositories {

    private Repositories() {
    }

    public interface OrderSnapshots {

        void save(OrderSnapshot snapshot);

        Optional<OrderSnapshot> findByOrderId(String orderId);
    }

    public interface Tickets {

        /**
         * Writes each ticket at its deterministic id, replacing any document already
         * there. That is what makes a redelivered event harmless without needing a
         * transaction MongoDB standalone cannot give us.
         */
        void saveAll(List<Ticket> tickets);

        List<Ticket> findByOrderId(String orderId);
    }

    public interface Notifications {

        void save(Notification notification);
    }

    /** The inbox. Consumer group supplied by the adapter, from configuration. */
    public interface ProcessedEvents {

        boolean alreadyProcessed(String eventId);

        void record(String eventId);
    }
}
