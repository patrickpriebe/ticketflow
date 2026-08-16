package com.ticketflow.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.payment.application.port.in.RefundCancelledOrderUseCase;
import com.ticketflow.payment.domain.model.Money;
import com.ticketflow.payment.domain.model.PaymentMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Consome {@code ticketflow.orders.cancelled} e desfaz a cobrança.
 *
 * <p>O nome do bean dá nome ao binding: {@code orderCancelled-in-0}.
 *
 * <p>Tópico próprio, e não o mesmo do {@code ORDER_CREATED}, porque o motivo de
 * ler é o oposto: um começa uma cobrança, o outro a desfaz. Dividir um tópico
 * obrigaria este serviço a discriminar tipo dentro de um fluxo só, e um erro de
 * roteamento ali significa cobrar um pedido cancelado.
 *
 * <p>O parsing mora aqui; o caso de uso só vê valores de domínio.
 */
@Component("orderCancelled")
public class OrderCancelledListener implements Consumer<Message<String>> {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledListener.class);
    private static final String EXPECTED_TYPE = "ORDER_CANCELLED";

    private final RefundCancelledOrderUseCase refundCancelledOrder;
    private final ObjectMapper objectMapper;

    public OrderCancelledListener(RefundCancelledOrderUseCase refundCancelledOrder,
                                  ObjectMapper objectMapper) {
        this.refundCancelledOrder = refundCancelledOrder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void accept(Message<String> message) {
        RefundCancelledOrderUseCase.Command command = parse(message.getPayload());

        RefundCancelledOrderUseCase.Result result = refundCancelledOrder.execute(command);

        log.info("Cancelamento do pedido {}: {}", command.orderId(), result);
    }

    private RefundCancelledOrderUseCase.Command parse(String payload) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("ORDER_CANCELLED nao e JSON valido", e);
        }

        String eventType = text(envelope, "eventType");
        if (!EXPECTED_TYPE.equals(eventType)) {
            throw new IllegalArgumentException(
                    "eventType inesperado no topico de cancelamento: " + eventType);
        }

        JsonNode data = envelope.path("data");
        if (data.isMissingNode()) {
            throw new IllegalArgumentException("ORDER_CANCELLED sem data");
        }

        return new RefundCancelledOrderUseCase.Command(
                UUID.fromString(required(envelope, "eventId")),
                UUID.fromString(required(data, "orderId")),
                UUID.fromString(required(data, "customerId")),
                Money.of(new BigDecimal(required(data, "totalAmount")), required(data, "currency")),
                PaymentMethod.valueOf(required(data, "paymentMethod")),
                text(data, "reason"),
                Instant.parse(required(envelope, "occurredAt")));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String required(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ORDER_CANCELLED sem '%s'".formatted(field));
        }
        return value;
    }
}
