package com.ticketflow.order.application.port.in;

import com.ticketflow.order.domain.model.Order;

import java.util.UUID;

/**
 * Cancelamento pedido por quem comprou.
 *
 * <p>Diferente de {@code EXPIRED}, que é o sistema desistindo por prazo, este é o
 * cliente desistindo. Os dois liberam estoque; só este dispara compensação, porque
 * só neste caso pode haver dinheiro já cobrado.
 */
public interface CancelOrderUseCase {

    /**
     * @param requesterId quem está pedindo, derivado do token — nunca do corpo
     */
    Order execute(UUID orderId, UUID requesterId);
}
