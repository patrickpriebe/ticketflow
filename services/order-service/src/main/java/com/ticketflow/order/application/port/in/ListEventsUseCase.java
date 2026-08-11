package com.ticketflow.order.application.port.in;

import com.ticketflow.order.application.pagination.PageQuery;
import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.domain.model.EventStatus;
import com.ticketflow.order.domain.model.TicketEvent;

import java.util.Objects;

/** Driving port: browse the catalogue. */
public interface ListEventsUseCase {

    PageResult<TicketEvent> execute(Query query);

    /**
     * @param city   optional filter; null means every city
     * @param status optional filter; null means every status
     */
    record Query(String city, EventStatus status, PageQuery pageQuery) {

        public Query {
            Objects.requireNonNull(pageQuery, "page query is required");
            if (city != null && city.isBlank()) {
                city = null;
            }
        }
    }
}
