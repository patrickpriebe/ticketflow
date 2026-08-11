package com.ticketflow.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Payment Service - a worker with no public API.
 *
 * <p>Consumes ORDER_CREATED, charges the customer through the external gateway and
 * publishes PAGAMENTO_APROVADO or PAGAMENTO_RECUSADO. It never calls another
 * TicketFlow service; the only outbound HTTP it makes is to the gateway.
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
