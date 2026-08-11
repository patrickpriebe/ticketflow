package com.ticketflow.order.domain.model;

import com.ticketflow.order.domain.CatalogFixtures;
import com.ticketflow.order.domain.exception.EventNotOnSaleException;
import com.ticketflow.order.domain.exception.InsufficientInventoryException;
import com.ticketflow.order.domain.exception.InvalidOrderException;
import com.ticketflow.order.domain.exception.InvalidOrderStatusTransitionException;
import com.ticketflow.order.domain.exception.TicketCategoryNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.ticketflow.order.domain.CatalogFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The domain rules of an order, with no mocks and no Spring context - the cheapest
 * and most valuable tests in the service.
 */
class OrderTest {

    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);

    private Order place(TicketEvent event, List<RequestedItem> items) {
        return Order.place("idem-key-1", CatalogFixtures.customer(), event,
                PaymentMethod.CREDIT_CARD, items, NOW, PAYMENT_WINDOW);
    }

    @Nested
    @DisplayName("placing an order")
    class Placing {

        @Test
        @DisplayName("starts PENDING - the payment has not even been attempted yet")
        void startsPending() {
            TicketEvent event = CatalogFixtures.onSaleEvent();
            TicketCategory pista = event.categories().get(0);

            Order order = place(event, List.of(new RequestedItem(pista.id(), 2)));

            assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.expiresAt()).isEqualTo(NOW.plus(PAYMENT_WINDOW));
        }

        @Test
        @DisplayName("prices the order from the catalogue, never from the request")
        void pricesFromCatalogue() {
            TicketEvent event = CatalogFixtures.onSaleEvent();
            TicketCategory pista = event.categories().get(0); // 650.00

            Order order = place(event, List.of(new RequestedItem(pista.id(), 2)));

            assertThat(order.totalAmount()).isEqualTo(Money.of("1300.00", "BRL"));
            assertThat(order.items()).singleElement().satisfies(item -> {
                assertThat(item.unitPrice()).isEqualTo(Money.of("650.00", "BRL"));
                assertThat(item.subtotal()).isEqualTo(Money.of("1300.00", "BRL"));
                assertThat(item.categoryName()).isEqualTo("Pista");
            });
        }

        @Test
        @DisplayName("sums several categories into the total")
        void sumsSeveralCategories() {
            UUID eventId = UUID.randomUUID();
            TicketCategory pista = CatalogFixtures.category(
                    UUID.randomUUID(), eventId, "Pista", "650.00", 100);
            TicketCategory camarote = CatalogFixtures.category(
                    UUID.randomUUID(), eventId, "Camarote", "2400.00", 10);
            TicketEvent event = CatalogFixtures.onSaleEvent(eventId, List.of(pista, camarote));

            Order order = place(event, List.of(
                    new RequestedItem(pista.id(), 2),
                    new RequestedItem(camarote.id(), 1)));

            assertThat(order.totalAmount()).isEqualTo(Money.of("3700.00", "BRL"));
            assertThat(order.items()).hasSize(2);
        }

        @Test
        @DisplayName("holds the tickets so nobody else can buy them")
        void reservesInventory() {
            TicketEvent event = CatalogFixtures.onSaleEvent();
            TicketCategory pista = event.categories().get(0);

            place(event, List.of(new RequestedItem(pista.id(), 3)));

            assertThat(pista.reservedQuantity()).isEqualTo(3);
            assertThat(pista.availableQuantity()).isEqualTo(97);
            assertThat(pista.soldQuantity()).isZero();
        }

        @Test
        @DisplayName("opens the status history with the PENDING entry")
        void recordsInitialHistory() {
            TicketEvent event = CatalogFixtures.onSaleEvent();
            TicketCategory pista = event.categories().get(0);

            Order order = place(event, List.of(new RequestedItem(pista.id(), 1)));

            assertThat(order.statusHistory()).singleElement().satisfies(change -> {
                assertThat(change.fromStatus()).isNull();
                assertThat(change.toStatus()).isEqualTo(OrderStatus.PENDING);
                assertThat(change.occurredAt()).isEqualTo(NOW);
            });
        }
    }

    @Nested
    @DisplayName("placing an order is refused when")
    class Refusals {

        @Test
        @DisplayName("there are no items")
        void noItems() {
            assertThatThrownBy(() -> place(CatalogFixtures.onSaleEvent(), List.of()))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("at least one item");
        }

        @Test
        @DisplayName("the same category is sent twice")
        void duplicateCategory() {
            TicketEvent event = CatalogFixtures.onSaleEvent();
            UUID categoryId = event.categories().get(0).id();

            assertThatThrownBy(() -> place(event, List.of(
                    new RequestedItem(categoryId, 1),
                    new RequestedItem(categoryId, 2))))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("more than once");
        }

        @Test
        @DisplayName("the quantity exceeds the per-item limit")
        void quantityAboveLimit() {
            TicketEvent event = CatalogFixtures.onSaleEvent();
            UUID categoryId = event.categories().get(0).id();

            assertThatThrownBy(() -> place(event, List.of(new RequestedItem(categoryId, 11))))
                    .isInstanceOf(InvalidOrderException.class)
                    .hasMessageContaining("between 1 and 10");
        }

        @Test
        @DisplayName("the category belongs to another event")
        void categoryFromAnotherEvent() {
            TicketEvent event = CatalogFixtures.onSaleEvent();
            UUID strangerCategoryId = UUID.randomUUID();

            assertThatThrownBy(() -> place(event, List.of(new RequestedItem(strangerCategoryId, 1))))
                    .isInstanceOf(TicketCategoryNotFoundException.class);
        }

        @Test
        @DisplayName("the sales window has closed")
        void salesWindowClosed() {
            TicketEvent event = CatalogFixtures.salesClosedEvent();
            UUID categoryId = event.categories().get(0).id();

            assertThatThrownBy(() -> place(event, List.of(new RequestedItem(categoryId, 1))))
                    .isInstanceOf(EventNotOnSaleException.class)
                    .hasMessageContaining("sales window");
        }

        @Test
        @DisplayName("the event is still a draft")
        void eventNotOnSale() {
            TicketEvent event = CatalogFixtures.eventWithStatus(EventStatus.DRAFT);
            UUID categoryId = event.categories().get(0).id();

            assertThatThrownBy(() -> place(event, List.of(new RequestedItem(categoryId, 1))))
                    .isInstanceOf(EventNotOnSaleException.class)
                    .hasMessageContaining("not on sale");
        }

        @Test
        @DisplayName("there are not enough tickets left")
        void insufficientInventory() {
            UUID eventId = UUID.randomUUID();
            TicketCategory almostGone = CatalogFixtures.category(
                    UUID.randomUUID(), eventId, "Camarote", "2400.00", 2);
            TicketEvent event = CatalogFixtures.onSaleEvent(eventId, List.of(almostGone));

            assertThatThrownBy(() -> place(event, List.of(new RequestedItem(almostGone.id(), 4))))
                    .isInstanceOf(InsufficientInventoryException.class)
                    .hasMessageContaining("only 2 ticket(s) left, 4 were requested");
        }

        @Test
        @DisplayName("one bad line fails - and no other line stays reserved")
        void failingLineLeavesNoReservation() {
            UUID eventId = UUID.randomUUID();
            TicketCategory pista = CatalogFixtures.category(
                    UUID.randomUUID(), eventId, "Pista", "650.00", 100);
            TicketCategory camarote = CatalogFixtures.category(
                    UUID.randomUUID(), eventId, "Camarote", "2400.00", 1);
            TicketEvent event = CatalogFixtures.onSaleEvent(eventId, List.of(pista, camarote));

            assertThatThrownBy(() -> place(event, List.of(
                    new RequestedItem(pista.id(), 2),
                    new RequestedItem(camarote.id(), 5))))
                    .isInstanceOf(InsufficientInventoryException.class);

            // The good line must not have been held. Validation happens in a first
            // pass precisely so a later failure cannot leave inventory locked away.
            assertThat(pista.reservedQuantity()).isZero();
            assertThat(camarote.reservedQuantity()).isZero();
        }
    }

    @Nested
    @DisplayName("status transitions")
    class Transitions {

        private Order pendingOrder() {
            TicketEvent event = CatalogFixtures.onSaleEvent();
            return place(event, List.of(new RequestedItem(event.categories().get(0).id(), 1)));
        }

        @Test
        @DisplayName("PENDING becomes PAID and records what caused it")
        void pendingToPaid() {
            Order order = pendingOrder();
            UUID sourceEventId = UUID.randomUUID();

            order.markPaid(sourceEventId, NOW.plusSeconds(3));

            assertThat(order.status()).isEqualTo(OrderStatus.PAID);
            assertThat(order.statusHistory()).hasSize(2);
            assertThat(order.statusHistory().get(1).sourceEventId()).isEqualTo(sourceEventId);
            assertThat(order.updatedAt()).isEqualTo(NOW.plusSeconds(3));
        }

        @Test
        @DisplayName("PENDING becomes REJECTED carrying the gateway's reason")
        void pendingToRejected() {
            Order order = pendingOrder();

            order.markRejected("Card declined by issuer", UUID.randomUUID(), NOW.plusSeconds(5));

            assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
            assertThat(order.statusHistory().get(1).reason()).isEqualTo("Card declined by issuer");
        }

        @Test
        @DisplayName("a paid order cannot be rejected afterwards")
        void paidCannotBeRejected() {
            Order order = pendingOrder();
            order.markPaid(UUID.randomUUID(), NOW.plusSeconds(3));

            assertThatThrownBy(() -> order.markRejected("too late", UUID.randomUUID(), NOW.plusSeconds(9)))
                    .isInstanceOf(InvalidOrderStatusTransitionException.class)
                    .hasMessageContaining("from PAID to REJECTED");
        }

        @Test
        @DisplayName("a redelivered approval must not be applied twice")
        void paidIsNotReapplied() {
            // The consumer's idempotency check is the first line of defence; this is
            // the second. Kafka delivers at-least-once, so this WILL be exercised.
            Order order = pendingOrder();
            order.markPaid(UUID.randomUUID(), NOW.plusSeconds(3));

            assertThatThrownBy(() -> order.markPaid(UUID.randomUUID(), NOW.plusSeconds(4)))
                    .isInstanceOf(InvalidOrderStatusTransitionException.class);

            assertThat(order.statusHistory()).hasSize(2);
        }

        @Test
        @DisplayName("expires only while still pending")
        void expiresWhilePending() {
            Order order = pendingOrder();

            assertThat(order.isExpiredAt(NOW.plusSeconds(60))).isFalse();
            assertThat(order.isExpiredAt(NOW.plus(PAYMENT_WINDOW))).isTrue();

            order.markPaid(UUID.randomUUID(), NOW.plusSeconds(3));
            assertThat(order.isExpiredAt(NOW.plus(PAYMENT_WINDOW))).isFalse();
        }
    }
}
