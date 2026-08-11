package com.ticketflow.order.infrastructure.messaging;

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
 */
@Component
public class StreamBridgeMessagePublisher implements MessagePublisher {

    private final StreamBridge streamBridge;
    private final String bindingName;

    public StreamBridgeMessagePublisher(
            StreamBridge streamBridge,
            @org.springframework.beans.factory.annotation.Value(
                    "${ticketflow.outbox.output-binding:orderCreated-out-0}") String bindingName) {
        this.streamBridge = streamBridge;
        this.bindingName = bindingName;
    }

    @Override
    public void publish(String topic, String key, String payload, Map<String, Object> headers) {
        MessageBuilder<String> builder = MessageBuilder.withPayload(payload)
                // The Kafka record key. Without it messages would be spread round
                // robin and two events about the same order could be processed out
                // of order.
                .setHeader(KafkaHeaders.KEY, key.getBytes())
                .setHeader("contentType", "application/json");

        headers.forEach(builder::setHeader);

        Message<String> message = builder.build();
        if (!streamBridge.send(bindingName, message)) {
            throw new IllegalStateException("Broker rejected the message for topic " + topic);
        }
    }
}
