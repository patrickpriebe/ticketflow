package com.ticketflow.order.infrastructure.persistence.mapper;

import com.ticketflow.order.domain.model.Money;
import com.ticketflow.order.domain.model.TicketCategory;
import com.ticketflow.order.domain.model.TicketEvent;
import com.ticketflow.order.infrastructure.persistence.entity.TicketCategoryEntity;
import com.ticketflow.order.infrastructure.persistence.entity.TicketEventEntity;

import java.util.List;

/** Translates catalogue rows into domain objects. One-way: the catalogue is read-only here. */
public final class CatalogMapper {

    private CatalogMapper() {
    }

    public static TicketEvent toDomain(TicketEventEntity entity, List<TicketCategoryEntity> categories) {
        return new TicketEvent(
                entity.getId(),
                entity.getName(),
                entity.getVenue(),
                entity.getCity(),
                entity.getStartsAt(),
                entity.getSalesStartAt(),
                entity.getSalesEndAt(),
                entity.getStatus(),
                categories.stream().map(CatalogMapper::toDomain).toList());
    }

    public static TicketCategory toDomain(TicketCategoryEntity entity) {
        return new TicketCategory(
                entity.getId(),
                // Only the identifier is touched, so the lazy proxy stays uninitialised.
                entity.getTicketEvent().getId(),
                entity.getName(),
                Money.of(entity.getPriceAmount(), entity.getCurrency().trim()),
                entity.getTotalQuantity(),
                entity.getReservedQuantity(),
                entity.getSoldQuantity(),
                entity.getVersion());
    }
}
