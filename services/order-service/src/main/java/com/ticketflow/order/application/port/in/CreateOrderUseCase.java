package com.ticketflow.order.application.port.in;

import com.ticketflow.order.domain.model.Customer;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.PaymentMethod;
import com.ticketflow.order.domain.model.RequestedItem;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Driving port: place an order. */
public interface CreateOrderUseCase {

    Result execute(Command command);

    record Command(String idempotencyKey,
                   Customer customer,
                   UUID ticketEventId,
                   PaymentMethod paymentMethod,
                   List<RequestedItem> items) {

        public Command {
            Objects.requireNonNull(idempotencyKey, "idempotency key is required");
            Objects.requireNonNull(customer, "customer is required");
            Objects.requireNonNull(ticketEventId, "ticket event id is required");
            Objects.requireNonNull(paymentMethod, "payment method is required");
            items = List.copyOf(Objects.requireNonNull(items, "items are required"));
        }
    }

    /**
     * @param replayed true when the idempotency key had already been used, so no new
     *                 order was created. The web layer answers 200 instead of 202.
     */
    record Result(Order order, boolean replayed) {

        public static Result created(Order order) {
            return new Result(order, false);
        }

        public static Result replayed(Order order) {
            return new Result(order, true);
        }
    }
}
