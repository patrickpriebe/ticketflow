package com.ticketflow.order.infrastructure.web.dto;

import com.ticketflow.order.application.pagination.PageResult;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, PageMeta page) {

    public record PageMeta(int page, int size, long totalElements, int totalPages) {
    }

    public static <D, R> PageResponse<R> from(PageResult<D> result, Function<D, R> mapper) {
        return new PageResponse<>(
                result.content().stream().map(mapper).toList(),
                new PageMeta(result.page(), result.size(), result.totalElements(), result.totalPages()));
    }
}
