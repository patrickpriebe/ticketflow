package com.ticketflow.payment.application.port.out;

import java.util.UUID;

/**
 * Inbox dos webhooks do provedor.
 *
 * <p>Separado do {@link ProcessedEventRepository} porque são duas fontes de
 * repetição diferentes: o Kafka reentrega porque a entrega é at-least-once, e o
 * provedor reenvia porque não recebeu 200 nosso. Um webhook do Stripe pode
 * chegar duas vezes por rede lenta, por retentativa programada, ou porque
 * alguém clicou em "reenviar" no painel — e nenhuma dessas pode liquidar o
 * mesmo pagamento duas vezes.
 */
public interface WebhookEventRepository {

    boolean alreadyHandled(String provider, String eventId);

    void record(String provider, String eventId, String eventType, UUID paymentId);
}
