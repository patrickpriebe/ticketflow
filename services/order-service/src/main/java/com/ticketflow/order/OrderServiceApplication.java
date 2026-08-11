package com.ticketflow.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Order Service - the only TicketFlow service with a public REST API.
 *
 * <p>It accepts an order, persists it as PENDING and records an ORDER_CREATED
 * event in the outbox within the same transaction. It never waits for the
 * payment: the final status arrives later, through Kafka.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
