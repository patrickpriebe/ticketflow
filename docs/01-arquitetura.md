# Architecture

## The problem this design solves

Selling tickets has a brutal peak: when a big show opens, thousands of people press
"buy" in the same minute. If the order API sits waiting for the payment gateway to
answer, every request holds a thread for seconds and the system falls over exactly
when it matters.

TicketFlow starts from a different premise: **the Order Service never waits for the
payment.** It stores the order as `PENDING`, publishes an event and answers
`202 Accepted` in milliseconds. The payment happens later, in another process, at its
own pace.

That is the central decision of the project. If a synchronous HTTP call ever appears
between the three services, the premise is broken.

## The three services

```
                    ┌──────────────────────┐
   POST /orders     │                      │   ticketflow.orders.created
  ───────────────►  │    Order Service     ├──────────────────────────┐
   202 Accepted     │                      │   (key = orderId)        │
  ◄───────────────  │  PostgreSQL          │                          │
                    │  ticketflow_orders   │                          ▼
                    │                      │             ┌──────────────────────┐
                    └──────────▲───────────┘             │                      │
                               │                         │   Payment Service    │
                               │                         │                      │
                               │                         │  PostgreSQL          │
                               │                         │  ticketflow_payments │
                               │                         └──────────┬───────────┘
                               │                                    │
                               │                                    │ HTTP
                               │                                    ▼
                               │                         ┌──────────────────────┐
                               │                         │  External gateway    │
                               │                         │  (Stripe · WireMock) │
                               │                         └──────────────────────┘
                               │
                               │   ticketflow.payments.processed
                               └────────────┬───────────────────────┐
                                            │                       │
                                    (consumer group                 │
                                     order-service)                 ▼
                                                         ┌──────────────────────┐
                                                         │ Notification Service │
                                                         │                      │
                                                         │  MongoDB             │
                                                         │  ticketflow_notif... │
                                                         └──────────────────────┘
```

### Order Service — REST API

Owns the catalogue and the orders. It does two things:

- **Produces** `ORDER_CREATED` when an order comes in.
- **Consumes** `PAGAMENTO_APROVADO` / `PAGAMENTO_RECUSADO` to move the order from
  `PENDING` to `PAID` or `REJECTED`.

Consuming its own result is what lets `GET /orders/{id}` show the final status without
ever having asked the Payment Service anything.

### Payment Service — worker

It has one public route (the provider's webhook) and one authenticated read used by
the browser to confirm a card. Everything else it does is driven by Kafka: it consumes
`ORDER_CREATED`, calls the external gateway over HTTP and publishes the result. It is
the only service that talks to the outside world, which is why it is the target of the
WireMock integration tests — success, decline, timeout and 5xx, not just the happy path.

### Notification Service

Consumes `ORDER_CREATED` to build its read model, consumes `PAGAMENTO_APROVADO` to
issue the ticket, and consumes `PAGAMENTO_RECUSADO` to record the failure notice.
Everything is stored in MongoDB.

## Why Kafka and not a plain queue

Two different consumers need the same payment event — the Order Service to update the
status, the Notification Service to issue the ticket — each at its own pace and neither
aware of the other. With separate consumer groups they read the same topic
independently, and adding a fourth service tomorrow requires touching nobody who
publishes.

The message key is always the `orderId`. That keeps every event about one order in the
same partition and therefore strictly ordered: `PAGAMENTO_APROVADO` can never arrive
before the `ORDER_CREATED` of that order.

## At-least-once delivery, and what it forces

Kafka delivers *at-least-once*. A message can arrive twice (a rebalance, a failure
before the offset commit). Two defences in every consumer:

1. **A `processed_events` table/collection** — stores the `eventId` already handled per
   consumer group. A repeated event is discarded.
2. **Database constraints** — `payments.order_id` is `UNIQUE`, `tickets.ticketCode` is
   unique. If the logic fails, the database still refuses to charge twice.

## Transactional outbox

Saving the order in PostgreSQL and publishing to Kafka are two operations in different
systems. If the second fails, the order exists and nobody will ever charge it.

The solution here is the `outbox_messages` table: the order `INSERT` and the event
`INSERT` happen in the **same transaction**. A relay reads the `PENDING` rows and
publishes to Kafka afterwards. Either both exist, or neither does.

The price is that publishing becomes asynchronous and may duplicate — the relay
publishes, dies before marking `PUBLISHED`, and republishes. That is fine, because the
consumers are already idempotent by the previous section.

## Two databases, on purpose

| | PostgreSQL | MongoDB |
|---|---|---|
| Services | Order, Payment | Notification |
| Stores | orders, payments, catalogue | tickets, notification history |
| Why | transactional, relational data with invariants that need constraints and transactions | variable documents — each channel (email, SMS, push) has its own shape, and a ticket carries a *snapshot* of the event |

The Order and Payment services share the PostgreSQL container out of local convenience,
but each has its **own database**. Neither can see the other's tables — that is what
forces the conversation through Kafka.

Rule of thumb: a new entity that is clearly transactional goes to PostgreSQL, even if
there happens to be Mongo code nearby.
