package com.ticketflow.order.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.order.infrastructure.persistence.entity.OutboxMessageEntity;
import com.ticketflow.order.infrastructure.persistence.jpa.JpaOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-08-10T14:00:00Z");
    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private JpaOutboxRepository outbox;
    @Mock
    private MessagePublisher publisher;

    private OutboxRelay relay;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outbox, publisher, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), 50, MAX_ATTEMPTS);
        orderId = UUID.randomUUID();
    }

    private OutboxMessageEntity pendingMessage() {
        return pendingMessage(orderId);
    }

    private OutboxMessageEntity pendingMessage(UUID aggregateId) {
        return new OutboxMessageEntity(
                UUID.randomUUID(),
                "Order",
                aggregateId,
                "ORDER_CREATED",
                "ticketflow.orders.created",
                aggregateId.toString(),
                "{\"eventType\":\"ORDER_CREATED\"}",
                "{\"contentType\":\"application/json\"}",
                NOW.minusSeconds(5));
    }

    private void givenPending(OutboxMessageEntity... messages) {
        when(outbox.findDispatchable(eq(NOW), any(Pageable.class))).thenReturn(List.of(messages));
    }

    @Test
    @DisplayName("publishes a pending message keyed by the order id")
    void publishesKeyedByOrderId() {
        OutboxMessageEntity message = pendingMessage();
        givenPending(message);

        int handled = relay.dispatchBatch();

        assertThat(handled).isEqualTo(1);
        verify(publisher).publish(
                eq("ticketflow.orders.created"),
                // Ordering per order depends entirely on this key.
                eq(orderId.toString()),
                eq("{\"eventType\":\"ORDER_CREATED\"}"),
                anyMap());
    }

    @Test
    @DisplayName("marks the message PUBLISHED only after the broker accepted it")
    void marksPublishedAfterSend() {
        OutboxMessageEntity message = pendingMessage();
        givenPending(message);

        relay.dispatchBatch();

        assertThat(message.isPublished()).isTrue();
        assertThat(message.getPublishedAt()).isEqualTo(NOW);
        assertThat(message.getLastError()).isNull();
    }

    @Test
    @DisplayName("forwards the stored headers to the broker")
    void forwardsHeaders() {
        givenPending(pendingMessage());

        relay.dispatchBatch();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headers = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(anyString(), anyString(), anyString(), headers.capture());
        assertThat(headers.getValue()).containsEntry("contentType", "application/json");
    }

    @Test
    @DisplayName("a failed send is retried later, not lost")
    void schedulesRetryOnFailure() {
        OutboxMessageEntity message = pendingMessage();
        givenPending(message);
        doThrow(new IllegalStateException("broker down"))
                .when(publisher).publish(anyString(), anyString(), anyString(), anyMap());

        relay.dispatchBatch();

        assertThat(message.isPublished()).isFalse();
        assertThat(message.getStatus()).isEqualTo("PENDING");
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getLastError()).contains("broker down");
        // Backed off into the future so the next cycle does not hammer a dead broker.
        assertThat(message.getAvailableAt()).isAfter(NOW);
    }

    @Test
    @DisplayName("gives up as FAILED once the attempts run out")
    void givesUpAfterMaxAttempts() {
        OutboxMessageEntity message = pendingMessage();
        // Two failures already recorded; MAX_ATTEMPTS is 3, so this one is the last.
        message.markFailed("first", NOW.minusSeconds(2));
        message.markFailed("second", NOW.minusSeconds(1));
        givenPending(message);
        doThrow(new IllegalStateException("still down"))
                .when(publisher).publish(anyString(), anyString(), anyString(), anyMap());

        relay.dispatchBatch();

        assertThat(message.getStatus()).isEqualTo("FAILED");
        assertThat(message.getAttempts()).isEqualTo(3);
        // FAILED rows are left for a human; retrying forever would just hide the problem.
    }

    @Test
    @DisplayName("one bad message does not stop the rest of the batch")
    void keepsGoingAfterOneFailure() {
        OutboxMessageEntity broken = pendingMessage(UUID.randomUUID());
        OutboxMessageEntity healthy = pendingMessage(UUID.randomUUID());
        givenPending(broken, healthy);
        doThrow(new IllegalStateException("broker down"))
                .when(publisher).publish(anyString(), eq(broken.getPartitionKey()),
                        anyString(), anyMap());

        relay.dispatchBatch();

        assertThat(broken.isPublished()).isFalse();
        assertThat(healthy.isPublished()).isTrue();
    }

    @Test
    @DisplayName("does nothing when there is nothing pending")
    void noopWhenEmpty() {
        when(outbox.findDispatchable(eq(NOW), any(Pageable.class))).thenReturn(List.of());

        assertThat(relay.dispatchBatch()).isZero();
        verifyNoInteractions(publisher);
    }
}
