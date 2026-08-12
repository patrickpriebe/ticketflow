package com.ticketflow.order.application.port.in;

import com.ticketflow.order.domain.model.Order;

import java.util.UUID;

/**
 * Driving port: read one order.
 *
 * <p>This is what a client polls after receiving 202, and what the front-end renders
 * as the order timeline.
 */
public interface GetOrderUseCase {

    /**
     * @param requesterId quem está pedindo. A autorização é regra de aplicação, não
     *                    detalhe da camada web: um pedido só pode ser lido por quem
     *                    o fez.
     * @throws com.ticketflow.order.domain.exception.OrderNotFoundException se o
     *         pedido não existe <em>ou</em> não pertence ao solicitante. Responder
     *         403 revelaria que aquele id existe, o que já é informação demais.
     */
    Order execute(UUID orderId, UUID requesterId);
}
