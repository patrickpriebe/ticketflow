package com.ticketflow.notification.infrastructure.web;

import com.ticketflow.notification.application.usecase.FindMyTickets;
import com.ticketflow.notification.domain.model.Ticket;
import com.ticketflow.notification.infrastructure.security.CustomerIdentity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Leitura dos ingressos emitidos.
 *
 * <p>Único endpoint público do Notification Service. Ele continua sem receber
 * comando nenhum — o que ele faz é dirigido por evento; isto aqui é só a janela
 * para o resultado.
 *
 * <p>O dono sai do token, como no Order Service. Aceitar um `customerId` na query
 * transformaria a lista de ingressos de qualquer pessoa numa URL.
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final FindMyTickets findMyTickets;

    public TicketController(FindMyTickets findMyTickets) {
        this.findMyTickets = findMyTickets;
    }

    /**
     * @param orderId opcional. Sem ele, devolve todos os ingressos do cliente.
     */
    @GetMapping
    public TicketsResponse myTickets(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) String orderId) {
        // Não é `jwt.getSubject()` direto: o id do cliente é derivado do token pela
        // mesma regra do Order Service. Ler o `sub` cru aqui faria a busca usar uma
        // chave diferente da que gravou o ingresso.
        String requester = CustomerIdentity.of(jwt);

        List<Ticket> found = orderId == null || orderId.isBlank()
                ? findMyTickets.ofCustomer(requester)
                : findMyTickets.ofOrder(orderId, requester);

        return new TicketsResponse(found.stream().map(TicketResponse::from).toList());
    }

    public record TicketsResponse(List<TicketResponse> content) {
    }

    /**
     * O que o cliente pode ver do próprio ingresso.
     *
     * <p>{@code archiveLocation} fica de fora de propósito: onde guardamos a cópia
     * é detalhe de infraestrutura e não interessa a quem comprou.
     */
    public record TicketResponse(String id,
                                 String ticketCode,
                                 String orderId,
                                 String eventName,
                                 String categoryName,
                                 String holderName,
                                 String qrCodePayload,
                                 String status,
                                 Instant issuedAt) {

        static TicketResponse from(Ticket ticket) {
            return new TicketResponse(
                    ticket.id(),
                    ticket.ticketCode(),
                    ticket.orderId(),
                    ticket.eventSnapshot() == null ? null : ticket.eventSnapshot().name(),
                    ticket.ticketCategory() == null ? null : ticket.ticketCategory().name(),
                    ticket.holder().name(),
                    ticket.qrCodePayload(),
                    ticket.status().name(),
                    ticket.issuedAt());
        }
    }
}
