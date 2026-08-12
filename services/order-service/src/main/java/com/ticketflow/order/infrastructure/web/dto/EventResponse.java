package com.ticketflow.order.infrastructure.web.dto;

import com.ticketflow.order.domain.model.TicketCategory;
import com.ticketflow.order.domain.model.TicketEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Responses of the catalogue endpoints. */
public final class EventResponse {

    private EventResponse() {
    }

    public record Summary(UUID id,
                          String name,
                          String venue,
                          String city,
                          Instant startsAt,
                          String status,
                          MoneyResponse priceFrom) {

        public static Summary from(TicketEvent event) {
            return new Summary(
                    event.id(),
                    event.name(),
                    event.venue(),
                    event.city(),
                    event.startsAt(),
                    event.status().name(),
                    MoneyResponse.from(event.priceFrom()));
        }
    }

    public record Detail(UUID id,
                         String name,
                         String description,
                         String venue,
                         String city,
                         Instant startsAt,
                         String status,
                         MoneyResponse priceFrom,
                         Instant salesStartAt,
                         Instant salesEndAt,
                         List<Category> categories) {

        public static Detail from(TicketEvent event) {
            return new Detail(
                    event.id(),
                    event.name(),
                    event.description(),
                    event.venue(),
                    event.city(),
                    event.startsAt(),
                    event.status().name(),
                    MoneyResponse.from(event.priceFrom()),
                    event.salesStartAt(),
                    event.salesEndAt(),
                    event.categories().stream().map(Category::from).toList());
        }
    }

    public record Category(UUID id, String name, MoneyResponse price, int availableQuantity) {

        public static Category from(TicketCategory category) {
            return new Category(
                    category.id(),
                    category.name(),
                    MoneyResponse.from(category.price()),
                    category.availableQuantity());
        }
    }
}
