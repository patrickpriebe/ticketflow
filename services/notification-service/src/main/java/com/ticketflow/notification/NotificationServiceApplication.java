package com.ticketflow.notification;

import com.ticketflow.notification.application.port.out.Repositories;
import com.ticketflow.notification.application.usecase.HandlePaymentResult;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Notification Service - the end of the line.
 *
 * <p>Consumes both ORDER_CREATED (to know what was bought) and the payment outcome
 * (to know whether to print it). Publishes nothing, so it needs no outbox.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * The use case is a plain class, assembled here. No {@code @Service} in the
     * application layer - wiring is an infrastructure concern, and this is what keeps
     * it unit-testable with ordinary fakes.
     */
    @Bean
    public com.ticketflow.notification.application.usecase.FindMyTickets findMyTickets(
            Repositories.Tickets tickets) {
        return new com.ticketflow.notification.application.usecase.FindMyTickets(tickets);
    }

    @Bean
    public HandlePaymentResult handlePaymentResult(Repositories.OrderSnapshots snapshots,
                                                   Repositories.Tickets tickets,
                                                   Repositories.Notifications notifications,
                                                   Repositories.ProcessedEvents processedEvents,
                                                   com.ticketflow.notification.application.port.out.TicketArchive archive,
                                                   Clock clock) {
        return new HandlePaymentResult(snapshots, tickets, notifications, processedEvents, archive, clock);
    }
}
