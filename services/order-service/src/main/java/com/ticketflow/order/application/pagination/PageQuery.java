package com.ticketflow.order.application.pagination;

/**
 * Paging request, expressed without Spring Data so the application layer stays free
 * of framework types.
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page cannot be negative, got: " + page);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and %d, got %d".formatted(MAX_SIZE, size));
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }
}
