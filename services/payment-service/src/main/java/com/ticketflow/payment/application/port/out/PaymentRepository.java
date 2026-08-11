package com.ticketflow.payment.application.port.out;

import com.ticketflow.payment.domain.model.Payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Optional<Payment> findByOrderId(UUID orderId);

    /** Inserts a new payment. The UNIQUE on order_id is the final guard against double charging. */
    Payment save(Payment payment);

    /** Persists the outcome and any new attempts recorded on the aggregate. */
    Payment update(Payment payment);
}
