package com.ticketflow.payment.application.port.out;

import com.ticketflow.payment.domain.event.PaymentSettled;

/**
 * Driven port: announces a settled payment.
 *
 * <p>"Publish" means <em>write to the outbox</em>, in the same transaction as the
 * payment update - not "send to Kafka now". Sending directly would allow a payment
 * to be recorded whose event never left, or an event to be sent for a payment that
 * rolled back.
 */
public interface DomainEventPublisher {

    void publish(PaymentSettled event);
}
