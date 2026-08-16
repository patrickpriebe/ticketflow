package com.ticketflow.payment.application.usecase;

import com.ticketflow.payment.application.port.in.RefundCancelledOrderUseCase;
import com.ticketflow.payment.application.port.out.PaymentGateway;
import com.ticketflow.payment.application.port.out.PaymentGateway.RefundOutcome;
import com.ticketflow.payment.application.port.out.PaymentGateway.RefundRequest;
import com.ticketflow.payment.application.port.out.PaymentGateway.RefundResponse;
import com.ticketflow.payment.application.port.out.PaymentRepository;
import com.ticketflow.payment.application.port.out.ProcessedEventRepository;
import com.ticketflow.payment.application.port.out.UnitOfWork;
import com.ticketflow.payment.domain.exception.PaymentGatewayUnavailableException;
import com.ticketflow.payment.domain.model.Payment;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Desfaz a cobrança de um pedido cancelado.
 *
 * <p>São quatro situações, e a diferença entre elas é o que decide se alguém
 * fica sem dinheiro:
 *
 * <ul>
 *   <li><strong>Não existe cobrança.</strong> O cancelamento correu na frente do
 *       {@code ORDER_CREATED}. Grava uma cobrança já cancelada — é a única forma
 *       de impedir que o consumidor do pedido, ao receber a mensagem depois,
 *       cobre o cartão de quem já desistiu. Sem isso o dinheiro sairia e nada
 *       o traria de volta, porque este evento já teria sido consumido.</li>
 *   <li><strong>Existe e não foi cobrada.</strong> Fecha como cancelada, sem
 *       falar com o provedor: não há o que desfazer.</li>
 *   <li><strong>Existe e foi aprovada.</strong> Estorna. Este é o caso que
 *       justifica o desenho inteiro.</li>
 *   <li><strong>Existe e foi recusada.</strong> Ninguém pagou nada.</li>
 * </ul>
 *
 * <p>A chamada ao provedor acontece <strong>fora de transação</strong>, como no
 * {@code ProcessOrderPayment}: segurar uma conexão de banco durante uma chamada
 * externa é a forma clássica de um provedor lento derrubar o banco junto.
 *
 * <p>Quando o provedor não responde, o registro de inbox <strong>não</strong> é
 * gravado e a exceção deixa a mensagem ser reentregue. É por isso que a chave de
 * idempotência mandada ao provedor é o id da cobrança: a segunda tentativa
 * encontra o estorno que já existe em vez de devolver o dinheiro duas vezes.
 */
public class RefundCancelledOrder implements RefundCancelledOrderUseCase {

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final ProcessedEventRepository processedEvents;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RefundCancelledOrder(PaymentRepository payments,
                                PaymentGateway gateway,
                                ProcessedEventRepository processedEvents,
                                UnitOfWork unitOfWork,
                                Clock clock) {
        this.payments = Objects.requireNonNull(payments);
        this.gateway = Objects.requireNonNull(gateway);
        this.processedEvents = Objects.requireNonNull(processedEvents);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Result execute(Command command) {
        Objects.requireNonNull(command, "command is required");

        if (processedEvents.alreadyProcessed(command.eventId())) {
            return Result.IGNORED_DUPLICATE;
        }

        Optional<Payment> existing = payments.findByOrderId(command.orderId());

        if (existing.isEmpty()) {
            return unitOfWork.execute(() -> blockBeforeCreation(command));
        }

        Payment payment = existing.get();

        if (!payment.isApproved()) {
            return unitOfWork.execute(() -> closeWithoutRefund(command, payment));
        }

        // --- fora de qualquer transação, de propósito ---
        RefundResponse response = gateway.refund(new RefundRequest(
                payment.id(),
                payment.id().toString(),
                payment.gatewayTransactionId(),
                payment.amount().amount(),
                payment.amount().currency()));
        // ------------------------------------------------

        if (response.outcome() == RefundOutcome.UNAVAILABLE) {
            // Nada é gravado: a mensagem volta e o estorno é tentado de novo.
            throw new PaymentGatewayUnavailableException(command.orderId(), response.failureReason());
        }

        return unitOfWork.execute(() -> applyRefund(command, payment, response));
    }

    private Result blockBeforeCreation(Command command) {
        Payment blocked = Payment.cancelledBeforeCharge(
                command.orderId(),
                command.customerId(),
                command.amount(),
                command.paymentMethod(),
                command.reason(),
                clock.instant());

        payments.save(blocked);
        processedEvents.record(command.eventId());
        return Result.BLOCKED_BEFORE_CREATION;
    }

    private Result closeWithoutRefund(Command command, Payment payment) {
        if (payment.isSettled()) {
            // Recusada, já cancelada ou já estornada. Nada a fazer, e a mensagem
            // precisa parar de voltar.
            processedEvents.record(command.eventId());
            return Result.NOTHING_TO_REFUND;
        }

        // PENDING ou FAILED: nada saiu ainda. Fechar aqui é o que impede uma
        // entrega atrasada do ORDER_CREATED de reabrir a cobrança.
        payment.cancelBeforeCharge(command.reason(), clock.instant());
        payments.update(payment);
        processedEvents.record(command.eventId());
        return Result.CANCELLED_BEFORE_CHARGE;
    }

    private Result applyRefund(Command command, Payment payment, RefundResponse response) {
        if (response.outcome() == RefundOutcome.DECLINED) {
            // O provedor disse não, e insistir não muda isso. O evento é marcado
            // para não voltar para sempre — o que fica é uma cobrança APPROVED de
            // um pedido cancelado, que é justamente o que alguém precisa ver.
            processedEvents.record(command.eventId());
            return Result.NOTHING_TO_REFUND;
        }

        payment.markRefunded(response.refundId(), clock.instant());
        payments.update(payment);
        processedEvents.record(command.eventId());
        return Result.REFUNDED;
    }
}
