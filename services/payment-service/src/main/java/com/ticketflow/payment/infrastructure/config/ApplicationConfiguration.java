package com.ticketflow.payment.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow.payment.application.port.in.ProcessOrderPaymentUseCase;
import com.ticketflow.payment.application.port.out.DomainEventPublisher;
import com.ticketflow.payment.application.port.out.PaymentGateway;
import com.ticketflow.payment.application.port.out.PaymentRepository;
import com.ticketflow.payment.application.port.out.ProcessedEventRepository;
import com.ticketflow.payment.application.port.out.UnitOfWork;
import com.ticketflow.payment.application.strategy.BoletoPaymentStrategy;
import com.ticketflow.payment.application.strategy.CreditCardPaymentStrategy;
import com.ticketflow.payment.application.strategy.PaymentStrategies;
import com.ticketflow.payment.application.strategy.PixPaymentStrategy;
import com.ticketflow.payment.application.usecase.ProcessOrderPayment;
import com.stripe.StripeClient;
import com.ticketflow.payment.application.port.in.SettlePaymentFromProviderUseCase;
import com.ticketflow.payment.application.port.out.WebhookEventRepository;
import com.ticketflow.payment.application.usecase.SettlePaymentFromProvider;
import com.ticketflow.payment.infrastructure.gateway.HttpPaymentGateway;
import com.ticketflow.payment.infrastructure.gateway.StripePaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Assembles the application layer.
 *
 * <p>Use cases and strategies are plain classes with constructor parameters - no
 * {@code @Service}, no component scanning below {@code infrastructure}. Wiring them
 * is an infrastructure concern, and keeping it here is what lets every one of them be
 * unit-tested with ordinary fakes.
 */
@Configuration
public class ApplicationConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Registering the strategies is the only place that changes when a payment
     * method is added. {@link PaymentStrategies} refuses to start if one is missing.
     */
    @Bean
    public PaymentStrategies paymentStrategies() {
        return new PaymentStrategies(List.of(
                new CreditCardPaymentStrategy(),
                new PixPaymentStrategy(),
                new BoletoPaymentStrategy()));
    }

    @Bean
    @ConditionalOnProperty(name = "ticketflow.gateway.provider", havingValue = "http", matchIfMissing = true)
    public RestClient gatewayRestClient(@Value("${ticketflow.gateway.base-url}") String baseUrl,
                                        @Value("${ticketflow.gateway.connect-timeout}") Duration connectTimeout,
                                        @Value("${ticketflow.gateway.read-timeout}") Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        // Bounded on purpose: without a read timeout a hung provider would block a
        // consumer thread forever and orders would simply stop being processed.
        factory.setReadTimeout(readTimeout);

        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /**
     * O gateway simulado. Só existe quando o Stripe não está configurado, o que
     * mantém `docker compose up` funcionando para quem clona o projeto sem ter
     * conta em provedor nenhum.
     */
    @Bean
    @ConditionalOnProperty(name = "ticketflow.gateway.provider", havingValue = "http", matchIfMissing = true)
    public PaymentGateway httpPaymentGateway(RestClient gatewayRestClient,
                                             ObjectMapper objectMapper,
                                             io.micrometer.core.instrument.MeterRegistry registry) {
        return new HttpPaymentGateway(gatewayRestClient, objectMapper, registry);
    }

    /**
     * O provedor de verdade. Os dois adaptadores implementam a mesma porta, e o
     * caso de uso não sabe qual dos dois recebeu — que é o motivo de a porta
     * existir.
     */
    @Bean
    @ConditionalOnProperty(name = "ticketflow.gateway.provider", havingValue = "stripe")
    public PaymentGateway stripePaymentGateway(
            @Value("${ticketflow.stripe.secret-key}") String secretKey,
            @Value("${ticketflow.stripe.test-card-payment-method:}") String testCardPaymentMethod,
            io.micrometer.core.instrument.MeterRegistry registry) {

        return new StripePaymentGateway(
                StripeClient.builder().setApiKey(secretKey).build(),
                registry,
                testCardPaymentMethod);
    }

    @Bean
    public SettlePaymentFromProviderUseCase settlePaymentFromProviderUseCase(
            PaymentRepository payments,
            WebhookEventRepository webhookEvents,
            DomainEventPublisher eventPublisher,
            UnitOfWork unitOfWork,
            Clock clock,
            @Value("${ticketflow.gateway.name}") String gatewayName) {

        return new SettlePaymentFromProvider(payments, webhookEvents, eventPublisher,
                unitOfWork, clock, gatewayName);
    }

    @Bean
    public ProcessOrderPaymentUseCase processOrderPaymentUseCase(
            PaymentRepository payments,
            PaymentGateway gateway,
            PaymentStrategies strategies,
            DomainEventPublisher eventPublisher,
            ProcessedEventRepository processedEvents,
            UnitOfWork unitOfWork,
            Clock clock,
            @Value("${ticketflow.gateway.name}") String gatewayName) {
        return new ProcessOrderPayment(payments, gateway, strategies, eventPublisher,
                processedEvents, unitOfWork, clock, gatewayName);
    }
}
