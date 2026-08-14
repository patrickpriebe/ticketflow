package com.ticketflow.payment.infrastructure.web;

import com.ticketflow.payment.application.port.in.FindOrderPaymentUseCase;
import com.ticketflow.payment.infrastructure.security.CustomerIdentity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * A única rota deste serviço que um navegador chama.
 *
 * <p>Vale explicar por que ela existe aqui e não no Order Service. O
 * {@code client_secret} nasce no provedor e é conhecido só por quem falou com
 * ele. Levá-lo até o Order Service exigiria uma de duas coisas: uma chamada HTTP
 * entre serviços — proibida neste projeto, os três só se falam por evento — ou
 * mandar a credencial dentro de um evento Kafka, espalhando-a pelo tópico e pelo
 * banco de outro serviço.
 *
 * <p>A saída é o navegador falar com os três serviços diretamente, cada um dono
 * do que é dele. <strong>Os serviços continuam sem se falar</strong>, que é a
 * regra que importa.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final FindOrderPaymentUseCase findOrderPayment;

    public PaymentController(FindOrderPaymentUseCase findOrderPayment) {
        this.findOrderPayment = Objects.requireNonNull(findOrderPayment);
    }

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<PaymentView> byOrder(@PathVariable UUID orderId,
                                               @AuthenticationPrincipal Jwt token) {

        return findOrderPayment.execute(orderId, CustomerIdentity.of(token))
                .map(view -> ResponseEntity.ok(new PaymentView(
                        view.orderId().toString(),
                        view.method().name(),
                        view.status().name(),
                        view.clientSecret())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * @param clientSecret ausente quando não há o que confirmar. O front trata a
     *                     ausência como "ainda não dá para pagar" e volta a
     *                     perguntar — a cobrança pode nem ter sido criada ainda,
     *                     porque quem a cria é um consumidor de Kafka.
     */
    public record PaymentView(String orderId,
                              String method,
                              String status,
                              String clientSecret) {
    }
}
