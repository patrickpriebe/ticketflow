package com.ticketflow.order.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Request body of {@code POST /api/v1/orders}, mirroring CreateOrderRequest in
 * contracts/openapi/order-service.yaml.
 *
 * <p>Carries no prices: those are read from the catalogue. A client-supplied price
 * would be a client-supplied discount.
 */
public record CreateOrderRequest(
        @NotNull @Valid CustomerPayload customer,
        @NotNull UUID eventId,
        @NotNull String paymentMethod,
        @NotEmpty @Size(max = 10) @Valid List<ItemPayload> items) {

    public record CustomerPayload(
            @NotNull UUID id,
            @NotNull @Size(min = 2, max = 180) String name,
            @NotNull @Email @Size(max = 180) String email) {
    }

    public record ItemPayload(
            @NotNull UUID ticketCategoryId,
            @Min(1) @Max(10) int quantity) {
    }
}
