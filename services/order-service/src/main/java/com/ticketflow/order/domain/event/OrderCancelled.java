package com.ticketflow.order.domain.event;

import com.ticketflow.order.domain.model.Order;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * O fato de um pedido ter sido cancelado por quem o fez.
 *
 * <p>Este evento existe por uma razão que não é notificação: <strong>ele é o
 * gatilho da compensação</strong>. Cancelar um pedido e cobrar o cartão são
 * decisões tomadas por serviços diferentes, sem nada coordenando as duas, então
 * as duas podem acontecer. Quando acontecem, quem tem o dinheiro é quem precisa
 * devolvê-lo — e é o Payment Service que ouve isto.
 *
 * <p>Os dois cruzamentos possíveis, e por que os dois são resolvidos pelo mesmo
 * evento:
 *
 * <ul>
 *   <li><strong>O cancelamento chega antes da cobrança.</strong> O Payment marca
 *       a cobrança como cancelada e não chama o provedor. Ninguém paga nada.</li>
 *   <li><strong>O cancelamento chega depois.</strong> O dinheiro saiu, e a
 *       resposta certa é estorno — não impedir o cancelamento.</li>
 * </ul>
 *
 * <p>Travar o cancelamento até o gateway responder seria a solução ingênua, e
 * prenderia o cliente numa tela por causa de um provedor lento. Cobrança indevida
 * se resolve devolvendo o dinheiro, não recusando o pedido de quem já desistiu.
 *
 * @param eventId    identidade da mensagem, para os consumidores deduplicarem
 * @param occurredAt quando a pessoa cancelou, não quando isto for publicado
 */
public record OrderCancelled(UUID eventId, Instant occurredAt, Order order, String reason) {

    public static final String TYPE = "ORDER_CANCELLED";

    public OrderCancelled {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(order, "order is required");
        Objects.requireNonNull(reason, "reason is required");
    }

    public static OrderCancelled of(Order order, String reason, Instant occurredAt) {
        return new OrderCancelled(UUID.randomUUID(), occurredAt, order, reason);
    }
}
