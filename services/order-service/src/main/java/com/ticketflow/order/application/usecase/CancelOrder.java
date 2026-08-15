package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.port.in.CancelOrderUseCase;
import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.application.port.out.DomainEventPublisher;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.application.port.out.UnitOfWork;
import com.ticketflow.order.domain.event.OrderCancelled;
import com.ticketflow.order.domain.exception.OrderNotFoundException;
import com.ticketflow.order.domain.exception.TicketEventNotFoundException;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderItem;
import com.ticketflow.order.domain.model.TicketCategory;
import com.ticketflow.order.domain.model.TicketEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cancela um pedido a pedido de quem o fez.
 *
 * <p>Tudo numa transação só: a transição, a devolução do estoque e o evento no
 * outbox. Se qualquer parte falhar, nada aconteceu — e o cliente que apertou
 * cancelar recebe erro em vez de um pedido meio cancelado.
 *
 * <p><strong>O cancelamento não espera o pagamento.</strong> Essa é a decisão que
 * carrega a classe. Seria possível consultar o Payment Service antes de deixar
 * cancelar — e isso seria uma chamada síncrona entre serviços, proibida aqui, além
 * de prender o cliente numa tela pela lentidão de um provedor. Em vez disso o
 * pedido cancela na hora e o evento {@link OrderCancelled} vai para o outbox; quem
 * tem o dinheiro decide se devolve.
 *
 * <p>A consequência aceita conscientemente: por um instante pode existir um pedido
 * cancelado cujo cartão foi cobrado. É um estado real de sistemas distribuídos, e a
 * resposta para ele é estorno, não um bloqueio que finge que a corrida não existe.
 */
public class CancelOrder implements CancelOrderUseCase {

    private final OrderRepository orders;
    private final CatalogRepository catalog;
    private final DomainEventPublisher eventPublisher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CancelOrder(OrderRepository orders,
                       CatalogRepository catalog,
                       DomainEventPublisher eventPublisher,
                       UnitOfWork unitOfWork,
                       Clock clock) {
        this.orders = Objects.requireNonNull(orders);
        this.catalog = Objects.requireNonNull(catalog);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Order execute(UUID orderId, UUID requesterId) {
        Objects.requireNonNull(orderId, "order id is required");
        Objects.requireNonNull(requesterId, "requester id is required");

        return unitOfWork.execute(() -> cancel(orderId, requesterId));
    }

    private Order cancel(UUID orderId, UUID requesterId) {
        Order order = orders.findById(orderId)
                // Pedido de outra pessoa responde igual a pedido inexistente: um erro
                // diferente confirmaria a existência do pedido para quem sonda ids.
                .filter(found -> found.customer().id().equals(requesterId))
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        Instant now = clock.instant();

        // Deixa o domínio recusar transição inválida. Cancelar um pedido já pago
        // estoura aqui, e é o que se quer: pedido pago não se cancela, se estorna,
        // e esse é outro fluxo com outras regras.
        order.cancel("Cancelled by the customer", now);

        catalog.updateInventory(releaseReservations(order));
        orders.update(order);
        eventPublisher.publish(OrderCancelled.of(order, "Cancelled by the customer", now));

        return order;
    }

    private List<TicketCategory> releaseReservations(Order order) {
        TicketEvent ticketEvent = catalog.findById(order.ticketEventId())
                .orElseThrow(() -> new TicketEventNotFoundException(order.ticketEventId()));

        List<TicketCategory> touched = new ArrayList<>(order.items().size());
        for (OrderItem item : order.items()) {
            TicketCategory category = ticketEvent.requireCategory(item.ticketCategoryId());
            category.releaseReservation(item.quantity());
            touched.add(category);
        }
        return touched;
    }
}
