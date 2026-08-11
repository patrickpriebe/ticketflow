package com.ticketflow.payment.infrastructure.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

/** The narrow seam between the outbox relay and the broker, so the relay stays testable. */
public interface MessagePublisher {

    void publish(String topic, String key, String payload, Map<String, Object> headers);

    @Component
    class StreamBridgePublisher implements MessagePublisher {

        private final StreamBridge streamBridge;
        private final String bindingName;

        public StreamBridgePublisher(
                StreamBridge streamBridge,
                @Value("${ticketflow.outbox.output-binding}") String bindingName) {
            this.streamBridge = streamBridge;
            this.bindingName = bindingName;
        }

        @Override
        public void publish(String topic, String key, String payload, Map<String, Object> headers) {
            MessageBuilder<String> builder = MessageBuilder.withPayload(payload)
                    .setHeader(KafkaHeaders.KEY, key.getBytes())
                    .setHeader("contentType", "application/json");
            headers.forEach(builder::setHeader);

            Message<String> message = builder.build();
            if (!streamBridge.send(bindingName, message)) {
                throw new IllegalStateException("Broker rejected the message for topic " + topic);
            }
        }
    }
}
