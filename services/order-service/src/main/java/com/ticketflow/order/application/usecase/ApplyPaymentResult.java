package com.ticketflow.order.application.usecase;

import com.ticketflow.order.application.port.in.ApplyPaymentResultUseCase;
import com.ticketflow.order.application.port.out.CatalogRepository;
import com.ticketflow.order.application.port.out.OrderRepository;
import com.ticketflow.order.application.port.out.ProcessedEventRepository;
import com.ticketflow.order.application.port.out.UnitOfWork;
import com.ticketflow.order.domain.exception.OrderNotFoundException;
import com.ticketflow.order.domain.exception.TicketEventNotFoundException;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderItem;
import com.ticketflow.order.domain.model.TicketCategory;
import com.ticketflow.order.domain.model.TicketEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies a payment outcome to an order.
 *
 * <p>Everything happens in one transaction: the status change, the inventory move
 * and the inbox record. If any of it fails, none of it happened and the message will
 * be redelivered - which is safe precisely because the inbox record is part of the
 * same commit.
 *
 * <p>Approved reservations become sales; refused ones are released back to the pool,
 * so a declined card does not keep tickets off the market.
 */
public class ApplyPaymentResult implements ApplyPaymentResultUseCase {

    private final OrderRepository orders;
    private final CatalogRepository catalog;
    private final ProcessedEventRepository processedEvents;
    private final UnitOfWork unitOfWork;

    public ApplyPaymentResult(OrderRepository orders,
                              CatalogRepository catalog,
                              ProcessedEventRepository processedEvents,
                              UnitOfWork unitOfWork) {
        this.orders = Objects.requireNonNull(orders);
        this.catalog = Objects.requireNonNull(catalog);
        this.processedEvents = Objects.requireNonNull(processedEvents);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
    }

    @Override
    public Result execute(Command command) {
        Objects.requireNonNull(command, "command is required");
        return unitOfWork.execute(() -> apply(command));
    }

    private Result apply(Command command) {
        if (processedEvents.alreadyProcessed(command.eventId())) {
            return Result.IGNORED_DUPLICATE;
        }

        Order order = orders.findById(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        // O pedido já acabou antes da resposta chegar — cancelado pelo cliente ou
        // expirado pelo prazo. Não é erro, é corrida: as duas coisas estavam em voo
        // ao mesmo tempo e nada as coordenava.
        //
        // Sem este ramo, `markPaid` estoura numa transição inválida, a mensagem é
        // reentregue três vezes e termina na DLQ. O estado que isso deixa é o pior
        // possível: o cartão foi cobrado, o pedido está cancelado, e a única prova
        // disso está numa fila que ninguém lê.
        //
        // Quem devolve o dinheiro não é este serviço — é quem o cobrou. O
        // `ORDER_CANCELLED` publicado no cancelamento é o gatilho, e o Payment
        // Service estorna ao recebê-lo. Aqui basta absorver a resposta tardia sem
        // perdê-la e sem mentir sobre o estado do pedido.
        //
        // Só cancelado e expirado entram aqui, não "terminal" inteiro: um pedido
        // já PAID recebendo outra aprovação é inconsistência de quem publicou, e
        // continua estourando abaixo. Absorvê-lo aqui mandaria estornar uma compra
        // legítima — foi o que o teste `alreadyPaidOrder` pegou.
        if (order.status().isClosedWithoutPayment()) {
            processedEvents.record(command.eventId());
            return command.approved() ? Result.PAID_AFTER_CLOSE : Result.IGNORED_CLOSED;
        }

        // Transition first: an illegal transition must abort before anything else is
        // touched, and it says something is wrong upstream rather than here.
        if (command.approved()) {
            order.markPaid(command.eventId(), command.occurredAt());
        } else {
            order.markRejected(command.failureReason(), command.eventId(), command.occurredAt());
        }

        catalog.updateInventory(settleInventory(order, command.approved()));
        orders.update(order);
        processedEvents.record(command.eventId());

        return Result.APPLIED;
    }

    private List<TicketCategory> settleInventory(Order order, boolean approved) {
        TicketEvent ticketEvent = catalog.findById(order.ticketEventId())
                .orElseThrow(() -> new TicketEventNotFoundException(order.ticketEventId()));

        List<TicketCategory> touched = new ArrayList<>(order.items().size());
        for (OrderItem item : order.items()) {
            TicketCategory category = ticketEvent.requireCategory(item.ticketCategoryId());
            if (approved) {
                category.confirmSale(item.quantity());
            } else {
                category.releaseReservation(item.quantity());
            }
            touched.add(category);
        }
        return touched;
    }
}
