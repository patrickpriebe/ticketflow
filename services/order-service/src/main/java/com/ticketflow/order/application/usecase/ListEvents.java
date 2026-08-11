package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.application.port.in.ListEventsUseCase;
import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.domain.model.TicketEvent;

import java.util.Objects;

public class ListEvents implements ListEventsUseCase {

    private final CatalogRepository catalog;

    public ListEvents(CatalogRepository catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    @Override
    public PageResult<TicketEvent> execute(Query query) {
        Objects.requireNonNull(query, "query is required");
        return catalog.search(query.city(), query.status(), query.pageQuery());
    }
}
