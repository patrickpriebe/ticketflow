package com.ticketflow.payment.application.usecase;

import com.ticketflow.payment.application.port.in.SettlePaymentFromProviderUseCase;
import com.ticketflow.payment.application.port.out.DomainEventPublisher;
import com.ticketflow.payment.application.port.out.PaymentRepository;
import com.ticketflow.payment.application.port.out.UnitOfWork;
import com.ticketflow.payment.application.port.out.WebhookEventRepository;
import com.ticketflow.payment.domain.event.PaymentSettled;
import com.ticketflow.payment.domain.model.Payment;
import com.ticketflow.payment.domain.model.PaymentAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Encerra um pagamento a partir do aviso do provedor.
 *
 * <p>Tudo numa transação só, e a ordem importa: liquidar o pagamento, escrever o
 * evento no outbox e registrar o inbox do webhook precisam cair juntos ou não
 * cair. Se o inbox fosse gravado antes, uma queda no meio marcaria o webhook como
 * tratado sem ter publicado nada — e o pedido ficaria PENDING para sempre, com o
 * provedor sem motivo para reenviar.
 *
 * <p>Três situações não são erro e por isso não lançam: webhook repetido,
 * pagamento já liquidado e transação desconhecida. Todas devolvem 200 para o
 * provedor, porque a alternativa é ele reenviar indefinidamente algo que nunca
 * vai mudar.
 */
public class SettlePaymentFromProvider implements SettlePaymentFromProviderUseCase {

    private static final Logger log = LoggerFactory.getLogger(SettlePaymentFromProvider.class);

    private final PaymentRepository payments;
    private final WebhookEventRepository webhookEvents;
    private final DomainEventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;
    private final String gatewayName;

    public SettlePaymentFromProvider(PaymentRepository payments,
                                     WebhookEventRepository webhookEvents,
                                     DomainEventPublisher eventPublisher,
                                     UnitOfWork unitOfWork,
                                     Clock clock,
                                     String gatewayName) {
        this.payments = Objects.requireNonNull(payments);
        this.webhookEvents = Objects.requireNonNull(webhookEvents);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
        this.gatewayName = Objects.requireNonNull(gatewayName);
    }

    @Override
    public Result execute(Command command) {
        Objects.requireNonNull(command, "command is required");

        if (webhookEvents.alreadyHandled(command.provider(), command.providerEventId())) {
            return Result.IGNORED_DUPLICATE;
        }

        return unitOfWork.execute(() -> settle(command));
    }

    private Result settle(Command command) {
        Optional<Payment> found = payments.findByGatewayTransaction(gatewayName, command.transactionId());

        if (found.isEmpty()) {
            // Registrado mesmo assim: sem isso o provedor reenviaria para sempre um
            // evento que nunca vai encontrar dono.
            webhookEvents.record(command.provider(), command.providerEventId(),
                    command.eventType(), null);
            log.warn("Webhook {} para transacao desconhecida {}",
                    command.providerEventId(), command.transactionId());
            return Result.UNKNOWN_TRANSACTION;
        }

        Payment payment = found.get();

        if (payment.isSettled()) {
            // Caminho comum do cartão: ele resolve na própria chamada e o webhook
            // chega depois confirmando o que já sabíamos. Publicar de novo faria o
            // Order Service processar uma segunda liquidação do mesmo pedido.
            webhookEvents.record(command.provider(), command.providerEventId(),
                    command.eventType(), payment.id());
            return Result.ALREADY_SETTLED;
        }

        Instant now = clock.instant();
        PaymentAttempt attempt = new PaymentAttempt(
                payment.nextAttemptNumber(),
                command.outcome(),
                200,
                0,
                null,
                command.rawPayloadSummary(),
                command.failureReason());

        payment.applyGatewayOutcome(attempt, gatewayName, command.transactionId(),
                command.failureCode(), command.failureReason(), now);
        payments.update(payment);

        eventPublisher.publish(PaymentSettled.of(payment, now));
        webhookEvents.record(command.provider(), command.providerEventId(),
                command.eventType(), payment.id());

        log.info("Webhook {} liquidou o pagamento {} como {}",
                command.providerEventId(), payment.id(), payment.status());

        return payment.isApproved() ? Result.APPROVED : Result.REJECTED;
    }
}
