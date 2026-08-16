package com.ticketflow.payment.infrastructure.persistence;

import com.ticketflow.payment.infrastructure.persistence.jpa.JpaOutboxRepository;
import com.ticketflow.payment.infrastructure.persistence.jpa.JpaProcessedEventRepository;
import com.ticketflow.payment.infrastructure.persistence.jpa.JpaWebhookEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionSweeperTest {

    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

    @Mock
    private JpaOutboxRepository outbox;
    @Mock
    private JpaProcessedEventRepository processedEvents;
    @Mock
    private JpaWebhookEventRepository webhookEvents;

    private RetentionSweeper sweeper() {
        return new RetentionSweeper(outbox, processedEvents, webhookEvents,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(7), Duration.ofDays(30), Duration.ofDays(30), 500);
    }

    private void nothingToDelete() {
        when(outbox.deletePublishedBefore(any(), anyInt())).thenReturn(0);
        when(processedEvents.deleteProcessedBefore(any(), anyInt())).thenReturn(0);
        when(webhookEvents.deleteReceivedBefore(any(), anyInt())).thenReturn(0);
    }

    @Test
    @DisplayName("soma o que saiu das tres tabelas")
    void sumsAllThree() {
        when(outbox.deletePublishedBefore(any(), anyInt())).thenReturn(10);
        when(processedEvents.deleteProcessedBefore(any(), anyInt())).thenReturn(20);
        when(webhookEvents.deleteReceivedBefore(any(), anyInt())).thenReturn(5);

        assertThat(sweeper().sweep()).isEqualTo(35);
    }

    @Test
    @DisplayName("os dois inboxes sobrevivem mais que o outbox")
    void inboxesOutliveOutbox() {
        // Não é estética. Os dois registros de inbox são o que impede uma
        // reentrega de virar uma segunda cobrança — do Kafka num caso, do Stripe
        // no outro. Encurtar essas janelas abaixo do prazo de reentrega de quem
        // as alimenta reabre exatamente o defeito que elas fecham, e o sintoma
        // apareceria como cobrança duplicada muito depois, sem ligação aparente.
        nothingToDelete();

        sweeper().sweep();

        ArgumentCaptor<Instant> outboxCut = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> inboxCut = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> webhookCut = ArgumentCaptor.forClass(Instant.class);
        verify(outbox).deletePublishedBefore(outboxCut.capture(), anyInt());
        verify(processedEvents).deleteProcessedBefore(inboxCut.capture(), anyInt());
        verify(webhookEvents).deleteReceivedBefore(webhookCut.capture(), anyInt());

        assertThat(inboxCut.getValue()).isBefore(outboxCut.getValue());
        assertThat(webhookCut.getValue()).isBefore(outboxCut.getValue());
    }

    @Test
    @DisplayName("nada a apagar devolve zero")
    void nothingRemoved() {
        nothingToDelete();
        assertThat(sweeper().sweep()).isZero();
    }
}
