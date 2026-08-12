package com.ticketflow.order.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Corpo do {@code POST /api/v1/orders}.
 *
 * <p><strong>Não carrega quem está comprando.</strong> A identidade vem do token,
 * e aceitá-la aqui de volta seria reabrir exatamente o buraco que a autenticação
 * fechou. Também não carrega preço: esse vem do catálogo, senão um cliente
 * escolheria quanto pagar.
 */
public record CreateOrderRequest(
        @NotNull UUID eventId,
        @NotNull String paymentMethod,
        @NotEmpty @Size(max = 10) @Valid List<ItemPayload> items) {

    public record ItemPayload(
            @NotNull UUID ticketCategoryId,
            @Min(1) @Max(10) int quantity) {
    }
}
