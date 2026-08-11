package com.ticketflow.payment.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the timer that drives the outbox relay.
 *
 * <p>Switchable so a test can set {@code ticketflow.outbox.scheduling-enabled=false}
 * and call {@code dispatchBatch()} itself. The relay bean always exists - only the
 * timer is optional.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ticketflow.outbox.scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
