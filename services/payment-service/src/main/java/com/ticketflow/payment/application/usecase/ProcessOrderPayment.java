package com.ticketflow.payment.application.usecase;

import com.ticketflow.payment.application.port.in.ProcessOrderPaymentUseCase;
import com.ticketflow.payment.application.port.out.DomainEventPublisher;
import com.ticketflow.payment.application.port.out.PaymentGateway;
import com.ticketflow.payment.application.port.out.PaymentGateway.AuthorizationRequest;
import com.ticketflow.payment.application.port.out.PaymentGateway.AuthorizationResponse;
import com.ticketflow.payment.application.port.out.PaymentRepository;
import com.ticketflow.payment.application.port.out.ProcessedEventRepository;
import com.ticketflow.payment.application.port.out.UnitOfWork;
import com.ticketflow.payment.application.strategy.PaymentStrategies;
import com.ticketflow.payment.application.strategy.PaymentStrategy;
import com.ticketflow.payment.domain.event.PaymentSettled;
import com.ticketflow.payment.domain.exception.PaymentGatewayUnavailableException;
import com.ticketflow.payment.domain.model.AttemptOutcome;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentAttempt;
import com.ticketflow.payment.domain.model.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Charges an order and announces the outcome.
 *
 * <p>The shape of this method is the interesting part. It uses <strong>two separate
 * transactions with the network call in between</strong>:
 *
 * <ol>
 *   <li>create (or find) the payment and commit;</li>
 *   <li>call the gateway holding no database connection;</li>
 *   <li>apply the outcome, write the outbox event and record the inbox entry, all in
 *       one commit.</li>
 * </ol>
 *
 * <p>Wrapping the whole thing in one transaction would pin a connection for the
 * duration of an external call - the classic way a slow provider takes the database
 * down with it. Splitting it means a crash in between leaves a PENDING payment,
 * which the redelivered message picks up and finishes, because the inbox entry is
 * only written at the very end.
 */
public class ProcessOrderPayment implements ProcessOrderPaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessOrderPayment.class);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final PaymentStrategies strategies;
    private final DomainEventPublisher eventPublisher;
    private final ProcessedEventRepository processedEvents;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final String gatewayName;

    public ProcessOrderPayment(PaymentRepository payments,
                               PaymentGateway gateway,
                               PaymentStrategies strategies,
                               DomainEventPublisher eventPublisher,
                               ProcessedEventRepository processedEvents,
                               UnitOfWork unitOfWork,
                               Clock clock,
                               String gatewayName) {
        this.payments = Objects.requireNonNull(payments);
        this.gateway = Objects.requireNonNull(gateway);
        this.strategies = Objects.requireNonNull(strategies);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.processedEvents = Objects.requireNonNull(processedEvents);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
        this.gatewayName = Objects.requireNonNull(gatewayName);
    }

    @Override
    public Result execute(Command command) {
        Objects.requireNonNull(command, "command is required");

        if (processedEvents.alreadyProcessed(command.eventId())) {
            return Result.IGNORED_DUPLICATE;
        }

        Payment payment = unitOfWork.execute(() -> findOrCreate(command));

        if (payment.isSettled()) {
            // A previous delivery already got a final answer. Nothing to charge, but
            // this particular event still needs marking so it stops coming back.
            unitOfWork.executeVoid(() -> processedEvents.record(command.eventId()));
            return Result.ALREADY_SETTLED;
        }

        PaymentStrategy strategy = strategies.forMethod(payment.method());

        // --- outside any transaction, on purpose ---
        AuthorizationRequest request = strategy.buildRequest(payment, command.payer());
        AuthorizationResponse response = gateway.authorize(request);
        // -------------------------------------------

        // O pedido pode ter sido cancelado enquanto a cobrança estava em voo. O
        // `payment` em memória ainda diz PENDING; a linha no banco já é CANCELLED.
        //
        // Vale para QUALQUER resposta do provedor, não só para a aprovação. O
        // `update` mais abaixo recarrega a entidade e escreve o status que o
        // objeto em memória calculou — ou seja, ele passa por cima do
        // cancelamento sem o lock otimista notar, porque a versão que ele compara
        // é a que acabou de ler. Foi assim que um timeout transformou uma
        // cobrança CANCELLED em FAILED; e FAILED não é terminal, então a proteção
        // que impede cobrar um pedido cancelado tinha sido apagada em silêncio.
        //
        // Estornar aqui é possível porque este ponto está fora de transação, que
        // é exatamente o que uma chamada externa exige.
        if (cancelledMeanwhile(command)) {
            return response.outcome() == AttemptOutcome.APPROVED
                    ? refundChargeForCancelledOrder(command, payment, response)
                    : closeCancelledWithoutCharging(command, response);
        }

        Result result = unitOfWork.execute(() -> settle(command, payment, response, strategy));

        if (result == null) {
            // The attempt is committed; now let the message be redelivered so the
            // charge is tried again once the gateway recovers.
            throw new PaymentGatewayUnavailableException(command.orderId(), response.failureReason());
        }
        return result;
    }

    /** Relê a cobrança para ver se o cancelamento passou por aqui nesse meio-tempo. */
    private boolean cancelledMeanwhile(Command command) {
        return payments.findByOrderId(command.orderId())
                .map(current -> current.status() == PaymentStatus.CANCELLED)
                .orElse(false);
    }

    /**
     * O pedido foi cancelado e o provedor não aprovou nada.
     *
     * <p>Recusa, timeout ou erro: em nenhum desses casos há dinheiro conhecido a
     * devolver, e o pedido já acabou. A cobrança fica como está — {@code
     * CANCELLED} —, e é justamente isso que precisa ser preservado: sobrescrever
     * com {@code FAILED} devolveria a cobrança para um estado não-terminal, e uma
     * reentrega do {@code ORDER_CREATED} voltaria a chamar o provedor.
     *
     * <p>O evento é marcado como processado mesmo num timeout, contrariando a
     * regra geral de deixar reentregar. A regra existe para não perder uma
     * cobrança que ainda vale; aqui ela não vale mais, e insistir seria tentar
     * cobrar quem já desistiu.
     */
    private Result closeCancelledWithoutCharging(Command command, AuthorizationResponse response) {
        log.warn("Pedido {} foi cancelado durante a cobranca; provedor respondeu {} e nada sera cobrado",
                command.orderId(), response.outcome());

        return unitOfWork.execute(() -> {
            processedEvents.record(command.eventId());
            return Result.ALREADY_SETTLED;
        });
    }

    /**
     * Devolve o que acabou de ser cobrado de um pedido que já não existe.
     *
     * <p>A cobrança fica {@code CANCELLED} com o id do estorno junto: o dinheiro
     * saiu e voltou, e as duas coisas precisam estar registradas. Se o provedor
     * não devolver, nada é marcado e a mensagem volta — insistir é o certo aqui,
     * porque a alternativa é ficar com dinheiro que não é nosso.
     */
    private Result refundChargeForCancelledOrder(Command command, Payment payment,
                                                 AuthorizationResponse response) {
        PaymentGateway.RefundResponse refund = gateway.refund(new PaymentGateway.RefundRequest(
                payment.id(),
                payment.id().toString(),
                response.transactionId(),
                payment.amount().amount(),
                payment.amount().currency()));

        if (refund.outcome() == PaymentGateway.RefundOutcome.UNAVAILABLE) {
            throw new PaymentGatewayUnavailableException(command.orderId(), refund.failureReason());
        }

        return unitOfWork.execute(() -> {
            Payment current = payments.findByOrderId(command.orderId()).orElseThrow();
            current.recordRefundOfCancelledOrder(response.transactionId(), refund.refundId(),
                    gatewayName, clock.instant());
            payments.update(current);
            processedEvents.record(command.eventId());
            return Result.ALREADY_SETTLED;
        });
    }

    private Payment findOrCreate(Command command) {
        return payments.findByOrderId(command.orderId())
                .orElseGet(() -> payments.save(Payment.forOrder(
                        command.orderId(), command.payer().customerId(),
                        command.amount(), command.method(), clock.instant())));
    }

    /**
     * @return the result, or {@code null} when the caller should retry
     */
    private Result settle(Command command, Payment payment,
                          AuthorizationResponse response, PaymentStrategy strategy) {
        Instant now = clock.instant();

        PaymentAttempt attempt = new PaymentAttempt(
                payment.nextAttemptNumber(),
                response.outcome(),
                response.httpStatus(),
                response.latencyMs(),
                // The request body is not stored: masking it correctly is easy to get
                // wrong, and nothing here needs it.
                null,
                response.rawResponse(),
                response.failureReason());

        if (response.outcome() == AttemptOutcome.ACCEPTED) {
            // The provider took it and will answer by webhook. The inbox entry is
            // written so this ORDER_CREATED stops being redelivered — the work it
            // asked for is done. What is deliberately NOT done is publishing: there
            // is no outcome to announce yet, and inventing one here would confirm a
            // boleto before anyone paid it.
            payment.awaitProviderConfirmation(attempt, gatewayName, response.transactionId(), now);
            payments.update(payment);
            processedEvents.record(command.eventId());
            return Result.AWAITING_PROVIDER;
        }

        payment.applyGatewayOutcome(attempt, gatewayName, response.transactionId(),
                response.failureCode(), response.failureReason(), now);
        payments.update(payment);

        if (payment.isSettled()) {
            eventPublisher.publish(PaymentSettled.of(payment, now));
            processedEvents.record(command.eventId());
            return payment.isApproved() ? Result.APPROVED : Result.REJECTED;
        }

        // No usable answer. Whether to try again is the strategy's call: retrying a
        // card charge is safe thanks to the idempotency key, retrying a boleto would
        // issue a second slip.
        boolean retryable = response.outcome() != AttemptOutcome.TIMEOUT || strategy.retryOnTimeout();
        if (retryable) {
            return null;
        }

        processedEvents.record(command.eventId());
        return Result.ABANDONED;
    }
}
