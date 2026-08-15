package com.ticketflow.order.infrastructure.web;

import com.ticketflow.order.application.pagination.PageQuery;
import com.ticketflow.order.application.pagination.PageResult;
import com.ticketflow.order.application.port.in.CancelOrderUseCase;
import com.ticketflow.order.application.port.in.CreateOrderUseCase;
import com.ticketflow.order.application.port.in.GetOrderUseCase;
import com.ticketflow.order.application.port.in.ListOrdersUseCase;
import com.ticketflow.order.domain.model.Customer;
import com.ticketflow.order.domain.model.Order;
import com.ticketflow.order.domain.model.OrderStatus;
import com.ticketflow.order.domain.model.PaymentMethod;
import com.ticketflow.order.domain.model.RequestedItem;
import com.ticketflow.order.infrastructure.security.AuthenticatedCustomer;
import com.ticketflow.order.infrastructure.web.dto.CreateOrderRequest;
import com.ticketflow.order.infrastructure.web.dto.OrderResponse;
import com.ticketflow.order.infrastructure.web.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

/**
 * Endpoints de pedido. Todos exigem token.
 *
 * <p>Quem está comprando vem sempre de {@code jwt}, nunca do corpo nem de um
 * parâmetro. É a diferença entre uma API que confia no cliente e uma que não.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;
    private final ListOrdersUseCase listOrders;
    private final CancelOrderUseCase cancelOrder;

    public OrderController(CreateOrderUseCase createOrder,
                           GetOrderUseCase getOrder,
                           ListOrdersUseCase listOrders,
                           CancelOrderUseCase cancelOrder) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
        this.listOrders = listOrders;
        this.cancelOrder = cancelOrder;
    }

    /**
     * Responde <strong>202 Accepted</strong>, nunca 201.
     *
     * <p>O pedido existe e está PENDING; ninguém foi cobrado ainda. Repetir a mesma
     * {@code Idempotency-Key} devolve 200 com o pedido original.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 8, max = 80) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            UriComponentsBuilder uriBuilder) {

        Customer customer = AuthenticatedCustomer.from(jwt);
        CreateOrderUseCase.Result result = createOrder.execute(toCommand(idempotencyKey, customer, request));
        OrderResponse body = OrderResponse.from(result.order());

        if (result.replayed()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity
                .accepted()
                .location(uriBuilder.path("/api/v1/orders/{id}").build(result.order().id()))
                .body(body);
    }

    /**
     * Cancela o pedido de quem chamou.
     *
     * <p>{@code POST} e não {@code DELETE}: o pedido não deixa de existir, ele muda
     * de estado — e o registro daquele cancelamento é parte do que a tela mostra.
     *
     * <p>Devolve o pedido já cancelado, para a tela não precisar consultar de novo.
     * Cancelar um pedido já pago estoura transição inválida: pedido pago não se
     * cancela, se estorna, e esse é outro fluxo com outras regras.
     */
    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        UUID requester = AuthenticatedCustomer.from(jwt).id();
        return OrderResponse.from(cancelOrder.execute(orderId, requester));
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        UUID requester = AuthenticatedCustomer.from(jwt).id();
        return OrderResponse.from(getOrder.execute(orderId, requester));
    }

    /**
     * Lista os pedidos <em>de quem chamou</em>.
     *
     * <p>Não há parâmetro de cliente: aceitar um seria deixar qualquer pessoa listar
     * as compras de qualquer outra trocando um UUID na query string.
     */
    @GetMapping
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) OrderStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {

        UUID requester = AuthenticatedCustomer.from(jwt).id();
        PageResult<Order> orders = listOrders.execute(
                new ListOrdersUseCase.Query(requester, status, PageQuery.of(page, size)));
        return PageResponse.from(orders, OrderResponse::from);
    }

    private CreateOrderUseCase.Command toCommand(String idempotencyKey,
                                                 Customer customer,
                                                 CreateOrderRequest request) {
        List<RequestedItem> items = request.items().stream()
                .map(item -> new RequestedItem(item.ticketCategoryId(), item.quantity()))
                .toList();

        return new CreateOrderUseCase.Command(
                idempotencyKey,
                customer,
                request.eventId(),
                PaymentMethod.valueOf(request.paymentMethod()),
                items);
    }
}
