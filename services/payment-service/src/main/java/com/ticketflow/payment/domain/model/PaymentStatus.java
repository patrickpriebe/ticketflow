package com.ticketflow.payment.domain.model;

/**
 * Outcome of a payment attempt cycle.
 *
 * <p>{@link #REJECTED} and {@link #FAILED} are not the same thing and collapsing
 * them would destroy information:
 *
 * <ul>
 *   <li>{@code REJECTED} - the gateway answered "no" (no funds, expired card). It is
 *       a final answer and the customer has to be told.</li>
 *   <li>{@code FAILED} - the gateway never gave a usable answer (timeout, 5xx).
 *       Nobody knows whether the money moved, and a retry may still succeed.</li>
 * </ul>
 */
public enum PaymentStatus {

    PENDING,
    APPROVED,
    REJECTED,
    FAILED,
    /**
     * O pedido foi cancelado antes de o dinheiro sair. Nada foi cobrado e nada
     * será — esta cobrança nasceu morta de propósito.
     */
    CANCELLED,
    /** Foi cobrado e devolvido. É o desfecho da compensação. */
    REFUNDED;

    public boolean canTransitionTo(PaymentStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == APPROVED || target == REJECTED
                    || target == FAILED || target == CANCELLED;
            // Sem resposta utilizável, ainda pode virar uma; e se o pedido foi
            // cancelado no meio disso, fecha como cancelada.
            case FAILED -> target == APPROVED || target == REJECTED || target == CANCELLED;
            // A única saída de uma cobrança aprovada é a devolução. Não existe
            // "desaprovar": dinheiro que saiu se resolve estornando.
            case APPROVED -> target == REFUNDED;
            case REJECTED, CANCELLED, REFUNDED -> false;
        };
    }

    /**
     * Já existe uma resposta; não há o que perguntar ao provedor.
     *
     * <p>É isso que o {@code ProcessOrderPayment} consulta antes de cobrar, e é
     * por isso que {@link #CANCELLED} entra aqui: quando o cancelamento chega
     * antes do {@code ORDER_CREATED}, a cobrança é registrada já cancelada e o
     * consumidor do pedido a encontra pronta, sem nunca chamar o provedor.
     *
     * <p>{@link #APPROVED} continua sendo "final" mesmo podendo virar
     * {@link #REFUNDED}: o estorno é um fato novo, não a resposta que faltava.
     */
    public boolean isFinal() {
        return this != PENDING && this != FAILED;
    }
}
