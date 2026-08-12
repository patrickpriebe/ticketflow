package com.ticketflow.notification.application.usecase;

import com.ticketflow.notification.application.port.out.Repositories;
import com.ticketflow.notification.domain.model.Ticket;

import java.util.List;
import java.util.Objects;

/**
 * Os ingressos de um cliente.
 *
 * <p>Existe porque, sem ele, um cliente pagava e não tinha como ver o que comprou:
 * o ingresso era emitido, ganhava código e cópia no S3, e ficava preso no banco sem
 * nenhum caminho de leitura.
 *
 * <p>O id do solicitante nunca é opcional. Uma consulta sem dono devolveria os
 * ingressos de todo mundo, e a assinatura do método é o que impede alguém de
 * escrever isso por engano.
 */
public class FindMyTickets {

    private final Repositories.Tickets tickets;

    public FindMyTickets(Repositories.Tickets tickets) {
        this.tickets = Objects.requireNonNull(tickets);
    }

    /** Ingressos de um pedido específico. Lista vazia se o pedido não é do solicitante. */
    public List<Ticket> ofOrder(String orderId, String requesterId) {
        Objects.requireNonNull(orderId, "orderId é obrigatório");
        Objects.requireNonNull(requesterId, "requesterId é obrigatório");
        return tickets.findByOrderIdAndHolder(orderId, requesterId);
    }

    /** Todos os ingressos do solicitante. */
    public List<Ticket> ofCustomer(String requesterId) {
        Objects.requireNonNull(requesterId, "requesterId é obrigatório");
        return tickets.findByHolder(requesterId);
    }
}
