package com.ticketflow.order.domain.model;

public enum EventStatus {

    /** Being prepared; not visible for sale yet. */
    DRAFT,
    ON_SALE,
    SOLD_OUT,
    CANCELLED,
    FINISHED;

    public boolean acceptsOrders() {
        return this == ON_SALE;
    }
}
