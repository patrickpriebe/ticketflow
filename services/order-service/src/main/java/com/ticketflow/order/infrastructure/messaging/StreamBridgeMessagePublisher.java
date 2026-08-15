package com.ticketflow.order.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes through Spring Cloud Stream.
 *
 * <p>{@link StreamBridge} rather than a declared {@code Supplier} binding: the relay
 * sends when it has something to send, which is imperative by nature, while a
 * Supplier is polled by the framework on its own schedule.
 *
 * <p><strong>O destino vem da linha do outbox, não de uma configuração fixa.</strong>
 * Antes havia um único binding para tudo, e o {@code topic} gravado na linha era
 * ignorado — enquanto só existia um tópico isso funcionava por coincidência. Com o
 * {@code ORDER_CANCELLED} o defeito apareceria da pior forma possível: o evento de
 * cancelamento sairia em {@code orders.created}, o Payment Service o leria como um
 * pedido novo e cobraria o cartão de um pedido cancelado. Um tópico sem binding
 * agora estoura na hora, em vez de escolher o destino errado em silêncio.
 */
@Component
public class StreamBridgeMessagePublisher implements MessagePublisher {

    private final StreamBridge streamBridge;
    private final Map<String, String> bindings;

    public StreamBridgeMessagePublisher(
            StreamBridge streamBridge,
            @Value("#{${ticketflow.outbox.bindings}}") Map<String, String> bindings) {
        this.streamBridge = streamBridge;
        this.bindings = Map.copyOf(bindings);
    }

    @Override
    public void publish(String topic, String key, String payload, Map<String, Object> headers) {
        String binding = bindings.get(topic);
        if (binding == null) {
            throw new IllegalStateException(
                    "Nenhum binding configurado para o tópico " + topic
                            + "; configure ticketflow.outbox.bindings");
        }

        MessageBuilder<String> builder = MessageBuilder.withPayload(payload)
                // The Kafka record key. Without it messages would be spread round
                // robin and two events about the same order could be processed out
                // of order.
                .setHeader(KafkaHeaders.KEY, key.getBytes())
                .setHeader("contentType", "application/json");

        headers.forEach(builder::setHeader);

        Message<String> message = builder.build();
        if (!streamBridge.send(binding, message)) {
            throw new IllegalStateException("Broker rejected the message for topic " + topic);
        }
    }
}
