package com.ticketflow.notification.application.usecase;

import com.ticketflow.notification.application.port.out.Repositories;
import com.ticketflow.notification.application.port.out.TicketArchive;
import com.ticketflow.notification.domain.model.Notification;
import com.ticketflow.notification.domain.model.OrderSnapshot;
import com.ticketflow.notification.domain.model.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a payment outcome into tickets and a message to the customer.
 *
 * <p>Approved orders get one ticket per unit bought; refused ones get a notice and
 * nothing else. There is no transaction spanning the writes - MongoDB standalone has
 * none - so idempotence is bought with deterministic identifiers instead: replaying
 * the same event overwrites the same documents rather than creating new ones.
 *
 * <p>The inbox record is written last. A crash before it means the event is
 * redelivered and the work is redone harmlessly; writing it first would risk
 * swallowing an order's tickets forever.
 */
public class HandlePaymentResult {

    private static final Logger log = LoggerFactory.getLogger(HandlePaymentResult.class);

    private final Repositories.OrderSnapshots snapshots;
    private final Repositories.Tickets tickets;
    private final Repositories.Notifications notifications;
    private final Repositories.ProcessedEvents processedEvents;
    private final TicketArchive archive;
    private final Clock clock;

    public HandlePaymentResult(Repositories.OrderSnapshots snapshots,
                               Repositories.Tickets tickets,
                               Repositories.Notifications notifications,
                               Repositories.ProcessedEvents processedEvents,
                               TicketArchive archive,
                               Clock clock) {
        this.snapshots = Objects.requireNonNull(snapshots);
        this.tickets = Objects.requireNonNull(tickets);
        this.notifications = Objects.requireNonNull(notifications);
        this.processedEvents = Objects.requireNonNull(processedEvents);
        this.archive = Objects.requireNonNull(archive);
        this.clock = Objects.requireNonNull(clock);
    }

    public Result execute(Command command) {
        Objects.requireNonNull(command, "command is required");

        if (processedEvents.alreadyProcessed(command.eventId())) {
            return Result.IGNORED_DUPLICATE;
        }

        OrderSnapshot snapshot = snapshots.findByOrderId(command.orderId())
                .orElseThrow(() -> new IllegalStateException(
                        // The payment result overtook ORDER_CREATED. The partition
                        // key orders events within a topic, not across two of them,
                        // so nothing prevents this - failing lets the binder retry
                        // until the snapshot lands. Exhausting the retries sends the
                        // message to the DLQ, where it is visible rather than lost.
                        "No order snapshot for %s yet".formatted(command.orderId())));

        Instant now = clock.instant();
        Result result;

        if (command.approved()) {
            List<Ticket> issued = issueTickets(snapshot, now).stream()
                    // Archiving never throws and never blocks: a ticket that was
                    // paid for is valid whether or not the durable copy landed.
                    .map(ticket -> ticket.withArchiveLocation(archive.archive(ticket)))
                    .toList();
            tickets.saveAll(issued);
            notifications.save(Notification.ticketsIssued(
                    snapshot.orderId(), snapshot.customerId(), snapshot.customerEmail(),
                    snapshot.eventName(), issued.size(), now));
            log.info("Issued {} ticket(s) for order {}", issued.size(), snapshot.orderId());
            result = Result.TICKETS_ISSUED;
        } else {
            notifications.save(Notification.paymentRejected(
                    snapshot.orderId(), snapshot.customerId(), snapshot.customerEmail(),
                    command.failureReason(), now));
            log.info("Recorded refusal notice for order {}", snapshot.orderId());
            result = Result.REFUSAL_NOTIFIED;
        }

        processedEvents.record(command.eventId());
        return result;
    }

    private List<Ticket> issueTickets(OrderSnapshot snapshot, Instant now) {
        Ticket.EventSnapshot eventSnapshot = new Ticket.EventSnapshot(
                snapshot.eventName(), null, null);

        List<Ticket> issued = new ArrayList<>(snapshot.totalTickets());
        for (OrderSnapshot.Line line : snapshot.items()) {
            for (int seat = 1; seat <= line.quantity(); seat++) {
                issued.add(Ticket.issue(
                        snapshot.orderId(),
                        snapshot.eventId(),
                        eventSnapshot,
                        new Ticket.TicketCategory(line.ticketCategoryId(), line.categoryName()),
                        snapshot.holder(),
                        seat,
                        now));
            }
        }
        return issued;
    }

    /** @param failureReason only meaningful when {@code approved} is false */
    public record Command(String eventId, String orderId, boolean approved, String failureReason) {

        public Command {
            Objects.requireNonNull(eventId, "eventId is required");
            Objects.requireNonNull(orderId, "orderId is required");
        }
    }

    public enum Result {
        TICKETS_ISSUED,
        REFUSAL_NOTIFIED,
        IGNORED_DUPLICATE
    }
}
