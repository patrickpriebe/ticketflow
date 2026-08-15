package com.ticketflow.order.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of an order.
 *
 * <p>Only the transition into {@link #PENDING} is synchronous - it happens while the
 * customer's HTTP request is still open. Every other transition is driven by an
 * event consumed from Kafka, or by the expiry job.
 *
 * <p>The allowed transitions live here rather than scattered across services, so
 * "can a REJECTED order become PAID?" has exactly one answer in the codebase.
 */
public enum OrderStatus {

    PENDING,
    PAID,
    REJECTED,
    CANCELLED,
    EXPIRED;

    private static final Set<OrderStatus> FROM_PENDING =
            Collections.unmodifiableSet(EnumSet.of(PAID, REJECTED, CANCELLED, EXPIRED));

    /**
     * @return true if this status may legally become {@code target}.
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null || target == this) {
            return false;
        }
        // PENDING is the only non-terminal status: once an order is paid, rejected,
        // cancelled or expired, it stays that way.
        return this == PENDING && FROM_PENDING.contains(target);
    }

    public boolean isTerminal() {
        return this != PENDING;
    }

    /**
     * Acabou sem ninguém ter pago: o cliente desistiu ou o prazo venceu.
     *
     * <p>Não é o mesmo que {@link #isTerminal()}, e a diferença é dinheiro. Uma
     * aprovação que chega para um pedido <strong>cancelado ou expirado</strong> é
     * cobrança sem pedido, e pede estorno. Uma aprovação que chega para um pedido
     * já {@link #PAID} é outra coisa — inconsistência de quem publicou —, e tratar
     * as duas igual mandaria estornar uma compra legítima.
     */
    public boolean isClosedWithoutPayment() {
        return this == CANCELLED || this == EXPIRED;
    }
}
