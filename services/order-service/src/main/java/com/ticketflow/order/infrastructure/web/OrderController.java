package com.ticketflow.order.infrastructure.web;

import com.ticketflow.order.application.pagination.PageQuery;
import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.application.port.in.CreateOrderUseCase;
import com.ticketflow.order.application.port.in.GetOrderUseCase;
import com.ticketflow.order.application.port.in.ListOrdersUseCase;
import com.ticketflow.order.domain.model.Customer;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderStatus;
import com.ticketflow.order.domain.model.PaymentMethod;
import com.ticketflow.order.domain.model.RequestedItem;
import com.ticketflow.order.infrastructure.web.dto.CreateOrderRequest;
import com.ticketflow.order.infrastructure.web.dto.OrderResponse;
import com.ticketflow.order.infrastructure.web.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;
    private final ListOrdersUseCase listOrders;

    public OrderController(CreateOrderUseCase createOrder,
                           GetOrderUseCase getOrder,
                           ListOrdersUseCase listOrders) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
        this.listOrders = listOrders;
    }

    /**
     * Answers <strong>202 Accepted</strong>, never 201.
     *
     * <p>The order exists and is PENDING; nobody has been charged yet. Replaying the
     * same {@code Idempotency-Key} returns 200 with the original order, so a client
     * that retries after a timeout cannot create a second one.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 8, max = 80) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            UriComponentsBuilder uriBuilder) {

        CreateOrderUseCase.Result result = createOrder.execute(toCommand(idempotencyKey, request));
        OrderResponse body = OrderResponse.from(result.order());

        if (result.replayed()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity
                .accepted()
                .location(uriBuilder.path("/api/v1/orders/{id}").build(result.order().id()))
                .body(body);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return OrderResponse.from(getOrder.execute(orderId));
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam UUID customerId,
                                            @RequestParam(required = false) OrderStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {

        PageResult<Order> orders = listOrders.execute(
                new ListOrdersUseCase.Query(customerId, status, PageQuery.of(page, size)));
        return PageResponse.from(orders, OrderResponse::from);
    }

    private CreateOrderUseCase.Command toCommand(String idempotencyKey, CreateOrderRequest request) {
        List<RequestedItem> items = request.items().stream()
                .map(item -> new RequestedItem(item.ticketCategoryId(), item.quantity()))
                .toList();

        return new CreateOrderUseCase.Command(
                idempotencyKey,
                new Customer(request.customer().id(), request.customer().name(), request.customer().email()),
                request.eventId(),
                PaymentMethod.valueOf(request.paymentMethod()),
                items);
    }
}
