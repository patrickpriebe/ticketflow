package com.ticketflow.payment.application.port.in;

import com.ticketflow.payment.domain.model.Money;
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
     * @param amount       o valor da cobrança. Sai daqui para a tela poder dizer
     *                     <em>quanto</em> foi estornado — "estornado" sem número
     *                     manda o cliente conferir a fatura para saber o que
     *                     voltou, que é justamente o trabalho que a tela deveria
     *                     poupar
     * @param refunded     se o dinheiro saiu e voltou. Não dá para deduzir do
     *                     {@code status}: quando o cancelamento cruza uma cobrança
     *                     em voo, o estorno acontece e o status continua
     *                     {@code CANCELLED}, porque foi isso que aconteceu com o
     *                     pedido. Sem este campo a tela diria "nenhuma cobrança
     *                     foi feita" para quem foi cobrado
     * @param clientSecret nulo quando não há nada a confirmar
     */
    record View(UUID orderId,
                PaymentMethod method,
                PaymentStatus status,
                Money amount,
                boolean refunded,
                String clientSecret) {
    }
}
