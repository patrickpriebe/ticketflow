package com.ticketflow.payment.application.port.in;

import com.ticketflow.payment.domain.model.PaymentMethod;
import com.ticketflow.payment.domain.model.PaymentStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * A cobrança de um pedido, para quem é dono dele.
 *
 * <p>Existe por causa do cartão: o provedor devolve um segredo por cobrança, e é
 * com ele que o navegador confirma o cartão sem que o número passe por aqui.
 */
public interface FindOrderPaymentUseCase {

    /**
     * @param requesterId quem está pedindo, derivado do token — nunca do corpo
     *                    ou da query, senão qualquer um consulta a cobrança de
     *                    qualquer outro
     */
    Optional<View> execute(UUID orderId, UUID requesterId);

    /**
     * @param clientSecret nulo quando não há nada a confirmar
     */
    record View(UUID orderId,
                PaymentMethod method,
                PaymentStatus status,
                String clientSecret) {
    }
}
