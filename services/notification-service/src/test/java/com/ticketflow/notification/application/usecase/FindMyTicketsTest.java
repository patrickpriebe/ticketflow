package com.ticketflow.notification.application.usecase;

import com.ticketflow.notification.application.port.out.Repositories;
import com.ticketflow.notification.domain.model.Ticket;
import com.ticketflow.notification.domain.model.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FindMyTicketsTest {

    private static final String ORDER = "order-1";
    private static final String ME = "customer-1";

    @Mock
    private Repositories.Tickets tickets;

    private FindMyTickets findMyTickets() {
        return new FindMyTickets(tickets);
    }

    private Ticket ticket() {
        return new Ticket("t1", "TF-ABCDEFGHIJ", ORDER, "evt-1",
                new Ticket.EventSnapshot("Rock in Rio", null, null),
                new Ticket.TicketCategory("cat-1", "Pista"),
                new Ticket.Holder(ME, "Ana Souza", "ana@example.com"),
                "TF|t1", TicketStatus.ISSUED, Instant.now(), null);
    }

    @Test
    @DisplayName("busca os ingressos do pedido filtrando pelo dono na própria consulta")
    void filtersByHolderInTheQuery() {
        when(tickets.findByOrderIdAndHolder(ORDER, ME)).thenReturn(List.of(ticket()));

        assertThat(findMyTickets().ofOrder(ORDER, ME)).hasSize(1);

        // O dono entra na consulta, não numa conferência depois. Filtro esquecido em
        // algum caminho de chamada é como vazamento de dado acontece.
        verify(tickets).findByOrderIdAndHolder(ORDER, ME);
        verify(tickets, never()).findByOrderId(anyString());
    }

    @Test
    @DisplayName("pedido de outra pessoa devolve lista vazia, não os ingressos dela")
    void anotherCustomerGetsNothing() {
        when(tickets.findByOrderIdAndHolder(ORDER, "outro")).thenReturn(List.of());

        assertThat(findMyTickets().ofOrder(ORDER, "outro")).isEmpty();
    }

    @Test
    @DisplayName("lista todos os ingressos do cliente")
    void listsAllMine() {
        when(tickets.findByHolder(ME)).thenReturn(List.of(ticket(), ticket()));

        assertThat(findMyTickets().ofCustomer(ME)).hasSize(2);
    }

    @Test
    @DisplayName("não existe consulta sem solicitante")
    void requesterIsMandatory() {
        // Uma consulta sem dono devolveria os ingressos de todo mundo; a assinatura
        // impede que alguém escreva isso por engano.
        assertThatThrownBy(() -> findMyTickets().ofCustomer(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> findMyTickets().ofOrder(ORDER, null))
                .isInstanceOf(NullPointerException.class);
    }
}
