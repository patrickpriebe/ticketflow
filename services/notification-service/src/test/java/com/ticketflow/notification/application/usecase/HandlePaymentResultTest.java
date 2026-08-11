package com.ticketflow.notification.application.usecase;

import com.ticketflow.notification.application.port.out.Repositories;
import com.ticketflow.notification.domain.model.Notification;
import com.ticketflow.notification.domain.model.OrderSnapshot;
import com.ticketflow.notification.domain.model.Ticket;
import com.ticketflow.notification.domain.model.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandlePaymentResultTest {

    private static final Instant NOW = Instant.parse("2026-08-10T14:00:00Z");

    @Mock
    private Repositories.OrderSnapshots snapshots;
    @Mock
    private Repositories.Tickets tickets;
    @Mock
    private Repositories.Notifications notifications;
    @Mock
    private Repositories.ProcessedEvents processedEvents;

    private HandlePaymentResult handlePaymentResult;
    private String orderId;
    private String eventId;

    @BeforeEach
    void setUp() {
        handlePaymentResult = new HandlePaymentResult(
                snapshots, tickets, notifications, processedEvents,
                // Um arquivo fake que sempre "funciona", para provar que a
                // localização volta gravada no ingresso.
                ticket -> "s3://ticketflow-tickets/tickets/" + ticket.orderId() + "/" + ticket.id() + ".json",
                Clock.fixed(NOW, ZoneOffset.UTC));
        orderId = UUID.randomUUID().toString();
        eventId = UUID.randomUUID().toString();
    }

    private void givenSnapshot(int pistaQty, int camaroteQty) {
        List<OrderSnapshot.Line> items = camaroteQty > 0
                ? List.of(new OrderSnapshot.Line("cat-pista", "Pista", pistaQty),
                          new OrderSnapshot.Line("cat-camarote", "Camarote", camaroteQty))
                : List.of(new OrderSnapshot.Line("cat-pista", "Pista", pistaQty));

        when(snapshots.findByOrderId(orderId)).thenReturn(Optional.of(new OrderSnapshot(
                orderId, "cust-1", "Ana Souza", "ana.souza@example.com",
                "evt-1", "Rock in Rio 2026 - Dia 1", items, NOW)));
    }

    private HandlePaymentResult.Command approved() {
        return new HandlePaymentResult.Command(eventId, orderId, true, null);
    }

    @Test
    @DisplayName("issues one ticket per unit bought")
    void issuesOneTicketPerUnit() {
        givenSnapshot(2, 1);

        HandlePaymentResult.Result result = handlePaymentResult.execute(approved());

        assertThat(result).isEqualTo(HandlePaymentResult.Result.TICKETS_ISSUED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> issued = ArgumentCaptor.forClass(List.class);
        verify(tickets).saveAll(issued.capture());

        assertThat(issued.getValue()).hasSize(3);
        assertThat(issued.getValue()).allSatisfy(ticket -> {
            assertThat(ticket.status()).isEqualTo(TicketStatus.ISSUED);
            assertThat(ticket.ticketCode()).matches("^TF-[A-Z0-9]{10}$");
            assertThat(ticket.holder().email()).isEqualTo("ana.souza@example.com");
        });
        assertThat(issued.getValue()).extracting(t -> t.ticketCategory().name())
                .containsExactly("Pista", "Pista", "Camarote");
    }

    @Test
    @DisplayName("ticket ids are deterministic, so a replay overwrites instead of duplicating")
    void ticketIdsAreDeterministic() {
        givenSnapshot(2, 0);

        handlePaymentResult.execute(approved());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> first = ArgumentCaptor.forClass(List.class);
        verify(tickets).saveAll(first.capture());
        List<String> firstIds = first.getValue().stream().map(Ticket::id).toList();

        // Same order, same categories, same seats - MongoDB standalone has no
        // transactions, so identity is what carries idempotence here.
        List<String> recomputed = List.of(
                Ticket.deterministicId(orderId, "cat-pista", 1),
                Ticket.deterministicId(orderId, "cat-pista", 2));

        assertThat(firstIds).isEqualTo(recomputed);
        assertThat(firstIds).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("keeps where each ticket was archived")
    void recordsArchiveLocation() {
        givenSnapshot(2, 0);

        handlePaymentResult.execute(approved());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> issued = ArgumentCaptor.forClass(List.class);
        verify(tickets).saveAll(issued.capture());
        // A ausência dessa localização é o que um job de backfill procuraria.
        assertThat(issued.getValue()).allSatisfy(ticket ->
                assertThat(ticket.archiveLocation()).startsWith("s3://ticketflow-tickets/tickets/"));
    }

    @Test
    @DisplayName("records the notification that the tickets were issued")
    void recordsIssuedNotification() {
        givenSnapshot(2, 0);

        handlePaymentResult.execute(approved());

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(Notification.Type.TICKET_ISSUED);
        assertThat(sent.getValue().recipient()).isEqualTo("ana.souza@example.com");
        assertThat(sent.getValue().body()).contains("2 ingresso(s)");
    }

    @Test
    @DisplayName("a refusal notifies the customer and issues nothing")
    void refusalIssuesNoTickets() {
        givenSnapshot(2, 0);

        HandlePaymentResult.Result result = handlePaymentResult.execute(
                new HandlePaymentResult.Command(eventId, orderId, false, "Card declined by issuer"));

        assertThat(result).isEqualTo(HandlePaymentResult.Result.REFUSAL_NOTIFIED);
        // The one thing that must never happen on a refused payment.
        verify(tickets, never()).saveAll(anyList());

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(sent.capture());
        assertThat(sent.getValue().type()).isEqualTo(Notification.Type.PAYMENT_REJECTED);
        assertThat(sent.getValue().body()).contains("Card declined by issuer");
    }

    @Test
    @DisplayName("a redelivered event does nothing at all")
    void ignoresDuplicate() {
        when(processedEvents.alreadyProcessed(eventId)).thenReturn(true);

        HandlePaymentResult.Result result = handlePaymentResult.execute(approved());

        assertThat(result).isEqualTo(HandlePaymentResult.Result.IGNORED_DUPLICATE);
        verifyNoInteractions(snapshots, tickets, notifications);
    }

    @Test
    @DisplayName("fails when the order snapshot has not arrived yet, so the event is retried")
    void failsWithoutSnapshot() {
        when(snapshots.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handlePaymentResult.execute(approved()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No order snapshot");

        // Not marked as processed: the redelivery must find the snapshot and succeed.
        verify(processedEvents, never()).record(any());
        verifyNoInteractions(tickets);
    }

    @Test
    @DisplayName("marks the event processed only after the work is done")
    void recordsInboxLast() {
        givenSnapshot(1, 0);

        handlePaymentResult.execute(approved());

        var order = org.mockito.Mockito.inOrder(tickets, notifications, processedEvents);
        order.verify(tickets).saveAll(anyList());
        order.verify(notifications).save(any());
        // Writing the inbox first would risk swallowing an order's tickets forever
        // if the process died in between.
        order.verify(processedEvents).record(eventId);
    }
}
