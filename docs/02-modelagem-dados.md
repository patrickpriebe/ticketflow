# Data modelling

Source files (the migration is always the source of truth, not this document):

| Where | What |
|---|---|
| [`services/order-service/.../V1__init_order_schema.sql`](../services/order-service/src/main/resources/db/migration/V1__init_order_schema.sql) | `ticketflow_orders` schema |
| [`services/payment-service/.../V1__init_payment_schema.sql`](../services/payment-service/src/main/resources/db/migration/V1__init_payment_schema.sql) | `ticketflow_payments` schema |
| [`services/order-service/.../R__seed_demo_catalogue.sql`](../services/order-service/src/main/resources/db/seed/R__seed_demo_catalogue.sql) | demo catalogue (development only) |
| [`infra/mongo/init/01-init-notification-db.js`](../infra/mongo/init/01-init-notification-db.js) | `ticketflow_notifications` collections |

---

## PostgreSQL — `ticketflow_orders`

```
events ──1:N──► ticket_categories ◄──N:1── order_items ──N:1──► orders ──1:N──► order_status_history
                                                                   │
                                                                   └── (same transaction) ──► outbox_messages
```

| Table | Role |
|---|---|
| `events` | Show, match, performance — what is being sold |
| `ticket_categories` | Price tiers of an event and the inventory of each |
| `orders` | The order; born `PENDING` |
| `order_items` | One row per purchased category |
| `order_status_history` | Append-only trail of status transitions |
| `outbox_messages` | Events waiting to be published to Kafka |
| `processed_events` | Idempotency inbox (the service also consumes) |

### Decisions worth explaining in an interview

**The price is copied into `order_items.unit_price`.** Yesterday's order must keep
showing yesterday's price. If the value came from a `JOIN` with `ticket_categories`, a
price change would rewrite everybody's history.

**`orders.idempotency_key UNIQUE`.** The client sends an `Idempotency-Key` header. If
the network drops after the server has already written, the retry hits the constraint
and returns the original order instead of creating a second one. Without it, a network
timeout becomes a double charge.

**`version BIGINT` on `orders` and `ticket_categories`.** JPA optimistic locking. Two
simultaneous purchases of the last ticket: one wins, the other gets an
`OptimisticLockException` and is rejected — instead of both selling the same seat.

**`CHECK (reserved_quantity + sold_quantity <= total_quantity)`.** The rule about not
selling more than exists lives in the database, not only in Java. An application bug
cannot write invalid state.

**The partial index `ix_outbox_dispatchable`.** The relay only looks at `PENDING` rows.
An index over the whole table would grow forever; the partial one indexes only what the
query uses, and shrinks as messages are published.

**Money in `NUMERIC(12,2)`, never `float`.** `0.1 + 0.2` in floating point is not `0.3`,
and in a financial amount that is a defect. It maps to `BigDecimal` in Java.

**Timestamps in `TIMESTAMPTZ`.** Stored in UTC and converted at the edge. A show at 9pm
in São Paulo and a server in another timezone cannot disagree about when sales close.

### Order lifecycle

```
                 ┌──────── PAGAMENTO_APROVADO ───────► PAID
   POST /orders  │
  ─────────────► PENDING ── PAGAMENTO_RECUSADO ──────► REJECTED
                 │
                 ├── customer cancels ───────────────► CANCELLED
                 └── expired without payment ────────► EXPIRED
```

Only the arrow into `PENDING` is synchronous. Every other one arrives through Kafka or
from the expiry job.

> `CANCELLED` is modelled and reachable in the domain, but no API route leads to it yet.
> See "Known limits" in the README.

---

## PostgreSQL — `ticketflow_payments`

| Table | Role |
|---|---|
| `payments` | One payment per order |
| `payment_attempts` | One row per call to the external gateway |
| `payment_webhook_events` | Inbox for provider callbacks |
| `processed_events` | Idempotency inbox |
| `outbox_messages` | Results waiting to be published |

**`payments.order_id UNIQUE`, with no foreign key.** The `UNIQUE` is the last barrier
against charging the same order twice. The absence of an FK is deliberate: the `orders`
table lives in another database, and that is what makes a cross-service `JOIN`
impossible.

**`payment_attempts` exists because of the tests.** It is what makes the WireMock
scenarios verifiable: a timeout test has to prove an attempt was recorded with
`outcome = 'TIMEOUT'`, not just that a method threw.

**`REJECTED` ≠ `FAILED` ≠ `ACCEPTED`.** `REJECTED` is the gateway saying no (a card
over its limit) — a final answer, and the customer must be told. `FAILED` is the
gateway not having said anything useful (timeout, 5xx) — a retry candidate. `ACCEPTED`
is the provider having taken it with the answer coming later, which is the boleto case.
Collapsing them into one status erases the information that decides what to do next.

**No card data in the database.** `payment_attempts.request_payload` stores a masked
request: brand and last four digits, never the PAN, CVV or expiry date.

---

## MongoDB — `ticketflow_notifications`

| Collection | Role |
|---|---|
| `order_snapshots` | CQRS read model built from `ORDER_CREATED` |
| `tickets` | Ticket issued after an approved payment |
| `notifications` | Delivery log (email today; SMS/push later) |
| `processed_events` | Idempotency inbox, with a 30-day TTL |

**`eventSnapshot` inside the ticket.** The ticket keeps a copy of the event's name,
venue and date. If the organiser renames the show later, an already issued ticket stays
readable — and reading it needs no access to the Order Service at all. This is exactly
the kind of denormalisation that justifies a document database.

**`$jsonSchema` on every collection.** "Schema-less" does not mean "no rules": the
validator rejects a ticket without a properly formatted `ticketCode`, or with a `status`
outside the enum. Without it, a serialisation bug enters the database silently.

**TTL on `processed_events`.** After 30 days a redelivery is implausible; without the
TTL the collection would grow forever. The equivalent PostgreSQL tables need a scheduled
cleanup for the same reason — recorded as a known limit in the README.

---

## Migration strategy

Flyway, with migrations inside `src/main/resources/db/migration` of each service — every
service owns its own schema, and in production the migration runs at application boot.

The Order Service's `db/seed` sits **outside** the default locations. Only the local
docker-compose adds that directory, so the demo catalogue can never leak into a real
environment. Being `R__` (repeatable), it runs again whenever it changes — which is why
every `INSERT` there is an upsert.
