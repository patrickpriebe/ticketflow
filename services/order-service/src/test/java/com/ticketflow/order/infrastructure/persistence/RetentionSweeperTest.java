package com.ticketflow.order.infrastructure.persistence;

import com.ticketflow.order.infrastructure.persistence.jpa.JpaOutboxRepository;
import com.ticketflow.order.infrastructure.persistence.jpa.JpaProcessedEventRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionSweeperTest {

    private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

    @Mock
    private JpaOutboxRepository outbox;
    @Mock
    private JpaProcessedEventRepository processedEvents;

    private RetentionSweeper sweeperWith(Duration outboxRetention, Duration inboxRetention) {
        return new RetentionSweeper(outbox, processedEvents, Clock.fixed(NOW, ZoneOffset.UTC),
                outboxRetention, inboxRetention, 500);
    }

    @Test
    @DisplayName("apaga a partir do corte de cada tabela, com o lote configurado")
    void deletesFromEachThreshold() {
        RetentionSweeper sweeper = sweeperWith(Duration.ofDays(7), Duration.ofDays(30));
        when(outbox.deletePublishedBefore(any(), anyInt())).thenReturn(120);
        when(processedEvents.deleteProcessedBefore(any(), anyInt())).thenReturn(80);

        int removed = sweeper.sweep();

        assertThat(removed).isEqualTo(200);

        ArgumentCaptor<Instant> outboxCut = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Integer> outboxBatch = ArgumentCaptor.forClass(Integer.class);
        org.mockito.Mockito.verify(outbox).deletePublishedBefore(outboxCut.capture(), outboxBatch.capture());
        assertThat(outboxCut.getValue()).isEqualTo(NOW.minus(Duration.ofDays(7)));
        assertThat(outboxBatch.getValue()).isEqualTo(500);

        ArgumentCaptor<Instant> inboxCut = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(processedEvents).deleteProcessedBefore(inboxCut.capture(), anyInt());
        assertThat(inboxCut.getValue()).isEqualTo(NOW.minus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("a janela do inbox e mais larga que a do outbox")
    void inboxOutlivesOutbox() {
        // A regra que este teste trava não é estética. O registro de inbox é o que
        // impede uma mensagem reentregue de ser processada de novo; enquanto o
        // Kafka ainda puder reentregá-la, ele precisa existir. Encurtar esta janela
        // abaixo da retenção do tópico reabre o defeito que a tabela fecha — e o
        // sintoma seria ingresso emitido duas vezes, muito depois, sem nada
        // ligando uma coisa à outra.
        RetentionSweeper sweeper = sweeperWith(Duration.ofDays(7), Duration.ofDays(30));
        when(outbox.deletePublishedBefore(any(), anyInt())).thenReturn(0);
        when(processedEvents.deleteProcessedBefore(any(), anyInt())).thenReturn(0);

        sweeper.sweep();

        ArgumentCaptor<Instant> outboxCut = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> inboxCut = ArgumentCaptor.forClass(Instant.class);
        org.mockito.Mockito.verify(outbox).deletePublishedBefore(outboxCut.capture(), anyInt());
        org.mockito.Mockito.verify(processedEvents).deleteProcessedBefore(inboxCut.capture(), anyInt());

        assertThat(inboxCut.getValue())
                .as("o corte do inbox tem que ser mais antigo — janela mais longa")
                .isBefore(outboxCut.getValue());
    }

    @Test
    @DisplayName("nada a apagar devolve zero, e e o que faz o agendador parar o ciclo")
    void nothingToDelete() {
        RetentionSweeper sweeper = sweeperWith(Duration.ofDays(7), Duration.ofDays(30));
        when(outbox.deletePublishedBefore(any(), anyInt())).thenReturn(0);
        when(processedEvents.deleteProcessedBefore(any(), anyInt())).thenReturn(0);

        assertThat(sweeper.sweep()).isZero();
    }
}
