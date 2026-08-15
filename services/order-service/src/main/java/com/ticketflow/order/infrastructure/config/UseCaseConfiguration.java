package com.ticketflow.order.infrastructure.config;

import com.ticketflow.order.application.port.in.ApplyPaymentResultUseCase;
import com.ticketflow.order.application.port.in.CancelOrderUseCase;
import com.ticketflow.order.application.port.in.CreateOrderUseCase;
import com.ticketflow.order.application.port.in.ExpirePendingOrdersUseCase;
import com.ticketflow.order.application.port.in.GetEventUseCase;
import com.ticketflow.order.application.port.in.GetOrderUseCase;
import com.ticketflow.order.application.port.in.ListEventsUseCase;
import com.ticketflow.order.application.port.in.ListOrdersUseCase;
import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.application.port.out.DomainEventPublisher;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.application.port.out.ProcessedEventRepository;
import com.ticketflow.order.application.port.out.UnitOfWork;
import com.ticketflow.order.application.usecase.ApplyPaymentResult;
import com.ticketflow.order.application.usecase.CancelOrder;
import com.ticketflow.order.application.usecase.CreateOrder;
import com.ticketflow.order.application.usecase.ExpirePendingOrders;
import com.ticketflow.order.application.usecase.GetEvent;
import com.ticketflow.order.application.usecase.GetOrder;
import com.ticketflow.order.application.usecase.ListEvents;
import com.ticketflow.order.application.usecase.ListOrders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Wires the use cases.
 *
 * <p>They are plain classes with constructor parameters - no {@code @Service}, no
 * component scanning of the application layer. Assembling them is an infrastructure
 * concern, and keeping it here is what lets every use case be unit-tested with
 * ordinary fakes.
 */
@Configuration
public class UseCaseConfiguration {

    /** Injected rather than called statically, so tests can freeze time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CreateOrderUseCase createOrderUseCase(OrderRepository orders,
                                                 CatalogRepository catalog,
                                                 DomainEventPublisher eventPublisher,
                                                 UnitOfWork unitOfWork,
                                                 Clock clock,
                                                 @Value("${ticketflow.order.payment-window}") Duration paymentWindow) {
        return new CreateOrder(orders, catalog, eventPublisher, unitOfWork, clock, paymentWindow);
    }

    @Bean
    public ApplyPaymentResultUseCase applyPaymentResultUseCase(OrderRepository orders,
                                                               CatalogRepository catalog,
                                                               ProcessedEventRepository processedEvents,
                                                               UnitOfWork unitOfWork) {
        return new ApplyPaymentResult(orders, catalog, processedEvents, unitOfWork);
    }

    /**
     * O cancelamento precisa do publicador porque o evento é o gatilho da
     * compensação — sem ele o pedido cancela e a cobrança segue viva.
     */
    @Bean
    public CancelOrderUseCase cancelOrderUseCase(OrderRepository orders,
                                                 CatalogRepository catalog,
                                                 DomainEventPublisher eventPublisher,
                                                 UnitOfWork unitOfWork,
                                                 Clock clock) {
        return new CancelOrder(orders, catalog, eventPublisher, unitOfWork, clock);
    }

    @Bean
    public ExpirePendingOrdersUseCase expirePendingOrdersUseCase(
            OrderRepository orders,
            CatalogRepository catalog,
            UnitOfWork unitOfWork,
            Clock clock,
            @Value("${ticketflow.order.expiry.batch-size:100}") int batchSize) {
        return new ExpirePendingOrders(orders, catalog, unitOfWork, clock, batchSize);
    }

    @Bean
    public GetOrderUseCase getOrderUseCase(OrderRepository orders) {
        return new GetOrder(orders);
    }

    // A autorização de "só vejo o meu pedido" mora no caso de uso, não no
    // controller: é regra de aplicação e precisa valer para qualquer entrada.

    @Bean
    public ListOrdersUseCase listOrdersUseCase(OrderRepository orders) {
        return new ListOrders(orders);
    }

    @Bean
    public ListEventsUseCase listEventsUseCase(CatalogRepository catalog) {
        return new ListEvents(catalog);
    }

    @Bean
    public GetEventUseCase getEventUseCase(CatalogRepository catalog) {
        return new GetEvent(catalog);
    }
}
