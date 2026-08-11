package com.ticketflow.order.infrastructure.web;

import com.ticketflow.order.application.pagination.PageQuery;
import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.application.port.in.GetEventUseCase;
import com.ticketflow.order.application.port.in.ListEventsUseCase;
import com.ticketflow.order.domain.model.EventStatus;
import com.ticketflow.order.domain.model.TicketEvent;
import com.ticketflow.order.infrastructure.web.dto.EventResponse;
import com.ticketflow.order.infrastructure.web.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final ListEventsUseCase listEvents;
    private final GetEventUseCase getEvent;

    public EventController(ListEventsUseCase listEvents, GetEventUseCase getEvent) {
        this.listEvents = listEvents;
        this.getEvent = getEvent;
    }

    @GetMapping
    public PageResponse<EventResponse.Summary> list(@RequestParam(required = false) String city,
                                                    @RequestParam(required = false) EventStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {

        PageResult<TicketEvent> events = listEvents.execute(
                new ListEventsUseCase.Query(city, status, PageQuery.of(page, size)));
        return PageResponse.from(events, EventResponse.Summary::from);
    }

    @GetMapping("/{eventId}")
    public EventResponse.Detail get(@PathVariable UUID eventId) {
        return EventResponse.Detail.from(getEvent.execute(eventId));
    }
}
