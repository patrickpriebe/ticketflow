package com.ticketflow.order.domain;

import com.ticketflow.order.domain.model.Customer;
import com.ticketflow.order.domain.model.EventStatus;
import com.ticketflow.order.domain.model.Money;
import com.ticketflow.order.domain.model.TicketCategory;
import com.ticketflow.order.domain.model.TicketEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builders for the objects every test needs. Keeping them here means a test reads
 * as the rule it is checking, instead of twenty lines of constructor noise.
 */
public final class CatalogFixtures {

    public static final Instant NOW = Instant.parse("2026-08-10T14:00:00Z");

    private CatalogFixtures() {
    }

    public static Customer customer() {
        return new Customer(UUID.randomUUID(), "Ana Souza", "ana.souza@example.com");
    }

    public static TicketCategory category(String name, String price, int total) {
        return category(UUID.randomUUID(), UUID.randomUUID(), name, price, total);
    }

    public static TicketCategory category(UUID id, UUID eventId, String name, String price, int total) {
        return new TicketCategory(id, eventId, name, Money.of(price, "BRL"), total, 0, 0, 0L);
    }

    /** An event on sale right now, with one "Pista" category of 100 tickets at 650.00. */
    public static TicketEvent onSaleEvent() {
        UUID eventId = UUID.randomUUID();
        return onSaleEvent(eventId, List.of(
                category(UUID.randomUUID(), eventId, "Pista", "650.00", 100)));
    }

    public static TicketEvent onSaleEvent(UUID eventId, List<TicketCategory> categories) {
        return new TicketEvent(
                eventId,
                "Rock in Rio 2026 - Dia 1",
                "Palco Mundo com line-up internacional.",
                "Parque Olimpico",
                "Rio de Janeiro",
                NOW.plusSeconds(86_400 * 30),
                NOW.minusSeconds(86_400),
                NOW.plusSeconds(86_400 * 29),
                EventStatus.ON_SALE,
                categories);
    }

    public static TicketEvent eventWithStatus(EventStatus status) {
        UUID eventId = UUID.randomUUID();
        return new TicketEvent(
                eventId,
                "Final do Campeonato Estadual",
                "Jogo decisivo, ingressos limitados.",
                "Arena Central",
                "Belo Horizonte",
                NOW.plusSeconds(86_400 * 60),
                NOW.minusSeconds(86_400),
                NOW.plusSeconds(86_400 * 59),
                status,
                List.of(category(UUID.randomUUID(), eventId, "Arquibancada", "90.00", 500)));
    }

    /** An event whose sales window has already closed. */
    public static TicketEvent salesClosedEvent() {
        UUID eventId = UUID.randomUUID();
        return new TicketEvent(
                eventId,
                "Sinfonica Municipal - Beethoven 9",
                "Nona sinfonia completa com coral.",
                "Theatro Municipal",
                "Sao Paulo",
                NOW.plusSeconds(3_600),
                NOW.minusSeconds(86_400 * 10),
                NOW.minusSeconds(3_600),
                EventStatus.ON_SALE,
                List.of(category(UUID.randomUUID(), eventId, "Plateia", "180.00", 200)));
    }
}
