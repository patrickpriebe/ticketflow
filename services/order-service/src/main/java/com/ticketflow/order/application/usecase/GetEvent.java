package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.port.in.GetEventUseCase;
import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.domain.exception.TicketEventNotFoundException;
import com.ticketflow.order.domain.model.TicketEvent;

import java.util.Objects;
import java.util.UUID;

public class GetEvent implements GetEventUseCase {

    private final CatalogRepository catalog;

    public GetEvent(CatalogRepository catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    @Override
    public TicketEvent execute(UUID ticketEventId) {
        Objects.requireNonNull(ticketEventId, "ticket event id is required");
        return catalog.findById(ticketEventId)
                .orElseThrow(() -> new TicketEventNotFoundException(ticketEventId));
    }
}
