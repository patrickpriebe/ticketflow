package com.ticketflow.order.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the scheduler that drives {@code OutboxRelay}.
 *
 * <p>Switchable so a test can set {@code ticketflow.outbox.scheduling-enabled=false}
 * and call {@code dispatchBatch()} itself. A test that had to race a background
 * thread would be flaky by construction.
 *
 * <p>The {@code OutboxRelay} bean itself always exists - only the timer is optional.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ticketflow.outbox.scheduling-enabled",
        havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {
}
