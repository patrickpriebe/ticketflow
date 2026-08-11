package com.ticketflow.order.application.port.out;

import com.ticketflow.order.application.pagination.PageQuery;
import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.domain.exception.ConcurrentInventoryUpdateException;
import com.ticketflow.order.domain.model.EventStatus;
import com.ticketflow.order.domain.model.TicketCategory;
import com.ticketflow.order.domain.model.TicketEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driven port: the event catalogue and its inventory. */
public interface CatalogRepository {

    Optional<TicketEvent> findById(UUID ticketEventId);

    PageResult<TicketEvent> search(String city, EventStatus status, PageQuery pageQuery);

    /**
     * Persists the reservation counters changed while placing an order.
     *
     * @throws ConcurrentInventoryUpdateException if another transaction changed the
     *                                            same categories first
     */
    void updateInventory(List<TicketCategory> categories);
}
