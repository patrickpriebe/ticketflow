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
   POST /orders     │                      │   orders.created    ──────────────┐
  ───────────────►  │    Order Service     ├───────────────────────────────────┤
   202 Accepted     │                      │   orders.cancelled  ──────────────┤
  ◄───────────────  │  PostgreSQL          │   (key = orderId)                 │
                    │  ticketflow_orders   │                                   ▼
                    │                      │                      ┌──────────────────────┐
                    └──────────▲───────────┘                      │                      │
                               │                                  │   Payment Service    │
                               │                                  │                      │
                               │                                  │  PostgreSQL          │
                               │                                  │  ticketflow_payments │
                               │                                  └──────────┬───────────┘
                               │                                             │
                               │                                             │ HTTP
                               │                                             ▼
                               │                                  ┌──────────────────────┐
                               │                                  │  External gateway    │
                               │                                  │  (Stripe · WireMock) │
                               │                                  └──────────────────────┘
                               │
                               │   payments.processed
                               ├─────────────────────────────────────────────┐
                               │                                             │
                       (group order-service)                     (group notification-service)
                                                                             ▼
                                                                  ┌──────────────────────┐
                                                                  │ Notification Service │
                                                                  │                      │
                                                                  │  MongoDB             │
                                                                  │  ticketflow_notif... │
                                                                  └──────────────────────┘
                                                                             ▲
                                          orders.created (group notification-service)
```

Three business topics, each with its own dead-letter queue. `orders.cancelled` is
separate from `orders.created` because a routing mistake between them means charging a
cancelled order — see [Compensation](#compensation-when-a-cancellation-crosses-a-charge).

### Order Service — REST API

Owns the catalogue and the orders. It does three things:

- **Produces** `ORDER_CREATED` when an order comes in.
- **Produces** `ORDER_CANCELLED` when the customer gives up — on its own topic, because
  the consumer reads it for the opposite reason: one starts a charge, the other undoes
  one.
- **Consumes** `PAGAMENTO_APROVADO` / `PAGAMENTO_RECUSADO` to move the order from
  `PENDING` to `PAID` or `REJECTED`.

Consuming its own result is what lets `GET /orders/{id}` show the final status without
ever having asked the Payment Service anything.

### Payment Service — worker

It has one public route (the provider's webhook) and one authenticated read used by
the browser to confirm a card and to ask whether a refund went through. Everything else
it does is driven by Kafka: it consumes `ORDER_CREATED`, calls the external gateway over
HTTP and publishes the result; and it consumes `ORDER_CANCELLED` to undo a charge. It is
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

## Compensation: when a cancellation crosses a charge

Cancelling is the hardest thing in the project, and the reason is one refusal: the Order
Service does **not** ask the Payment Service whether the card was already charged. That
would be the synchronous call this whole design forbids, and it would hold the customer
on a spinner because a provider is slow.

So the order cancels immediately and publishes `ORDER_CANCELLED`. Whoever holds the money
decides what to do with it. The consequence is accepted deliberately: **for a moment
there can be a cancelled order whose card was charged.** That is a real state in a
distributed system, and the answer to it is a refund — not a lock pretending the race
does not exist.

The event can then arrive at three different moments, and each one is a different bug if
you get it wrong.

**Before the charge exists.** The Payment Service writes a payment that is *already*
`CANCELLED`. The `UNIQUE (order_id)` constraint means the `ORDER_CREATED` arriving later
finds it settled and never calls the provider. Skip this and the card gets charged with
nothing left to refund it — the cancellation was already consumed and marked processed.

**After the charge was approved.** Refund, and store the provider's `refund_id` next to
the original transaction id. This is the case the design exists for.

**While the charge is in flight** — the one that produces no signal at all. The in-memory
payment still says `PENDING`, the row already says `CANCELLED`, and the money just left.
Without handling it: the update hits the optimistic lock, the message is redelivered, the
second delivery sees a settled payment and returns `ALREADY_SETTLED` without calling
anyone. Nobody errors, nothing reaches a dead-letter queue, and the card stays charged for
a cancelled order. `ProcessOrderPayment` re-reads the payment after the gateway answers
and refunds on the spot — which works precisely because that code path is deliberately
outside any transaction.

> The gateway call happens outside the transaction on purpose. Wrapping the flow in one
> transaction pins a database connection for the duration of an external call, which is
> the classic way a slow provider takes the database down with it.

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
