package com.ticketflow.order.application.port.in;

import com.ticketflow.order.domain.model.TicketEvent;

import java.util.UUID;

/** Driving port: one event with its categories and current availability. */
public interface GetEventUseCase {

    /**
     * @throws com.ticketflow.order.domain.exception.TicketEventNotFoundException if absent
     */
    TicketEvent execute(UUID ticketEventId);
}
