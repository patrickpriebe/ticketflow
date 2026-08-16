package com.ticketflow.payment.application.usecase;

import com.ticketflow.payment.application.port.in.FindOrderPaymentUseCase;
import com.ticketflow.payment.application.port.out.PaymentIntentReader;
import com.ticketflow.payment.application.port.out.PaymentRepository;
import com.ticketflow.payment.domain.model.Payment;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Devolve a cobrança do pedido para quem é dono dele.
 *
 * <p>Duas regras carregam esta classe inteira.
 *
 * <p><strong>Pedido de outra pessoa responde igual a pedido inexistente.</strong>
 * A mesma escolha do {@code GetOrder} no Order Service: um 403 aqui confirmaria
 * a existência do pedido para quem está sondando ids, e o simples fato de um
 * pedido existir já é informação de outra pessoa.
 *
 * <p><strong>Cobrança já resolvida não devolve segredo.</strong> Pago, recusado
 * ou cancelado, não há o que confirmar — e um segredo que continua saindo depois
 * de perder a utilidade é superfície exposta de graça.
 */
public class FindOrderPayment implements FindOrderPaymentUseCase {

    private final PaymentRepository payments;
    private final PaymentIntentReader intents;

    public FindOrderPayment(PaymentRepository payments, PaymentIntentReader intents) {
        this.payments = Objects.requireNonNull(payments, "payments is required");
        this.intents = Objects.requireNonNull(intents, "intents is required");
    }

    @Override
    public Optional<View> execute(UUID orderId, UUID requesterId) {
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(requesterId, "requester id is required");

        return payments.findByOrderId(orderId)
                .filter(payment -> payment.customerId().equals(requesterId))
                .map(this::toView);
    }

    private View toView(Payment payment) {
        return new View(
                payment.orderId(),
                payment.method(),
                payment.status(),
                payment.amount(),
                payment.wasRefunded(),
                clientSecretFor(payment));
    }

    private String clientSecretFor(Payment payment) {
        if (payment.isSettled() || payment.gatewayTransactionId() == null) return null;
        return intents.clientSecretFor(payment.gatewayTransactionId()).orElse(null);
    }
}
