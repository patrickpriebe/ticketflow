# Kafka events

Formal schemas in [`contracts/events/`](../contracts/events/).

## Topics

| Topic | Partitions | Retention | Produced by | Consumed by |
|---|---|---|---|---|
| `ticketflow.orders.created` | 3 | 7 days | order-service | payment-service, notification-service |
| `ticketflow.payments.processed` | 3 | 7 days | payment-service | order-service, notification-service |
| `ticketflow.orders.created.dlq` | 1 | 30 days | consumers | nobody (manual inspection) |
| `ticketflow.payments.processed.dlq` | 1 | 30 days | consumers | nobody (manual inspection) |

**Message key: always the `orderId`.** Every event about one order lands in the same
partition and stays ordered relative to the others. Without it, `PAGAMENTO_APROVADO`
could be processed before the `ORDER_CREATED` of the same order.

**3 partitions.** Allows up to 3 instances of a consumer group processing in parallel.
Increasing partitions later is easy; decreasing is not — so it starts at 3, which is
enough to demonstrate the parallelism without inflating the local environment.

**DLQs with 1 partition and long retention.** Ordering does not matter in a DLQ; what
matters is that the message does not disappear before somebody looks at it.

**`auto.create.topics.enable=false`.** A typo in a topic name must blow up, not create
a ghost topic nobody reads. Topics are created by
[`infra/kafka/create-topics.sh`](../infra/kafka/create-topics.sh).

## Envelope

Every message, on every topic, uses the same envelope — so any consumer can deduplicate
and trace without understanding the payload:

```json
{
  "eventId": "6f1a2b3c-4d5e-4f60-8a1b-2c3d4e5f6a7b",
  "eventType": "ORDER_CREATED",
  "eventVersion": 1,
  "occurredAt": "2026-08-10T14:03:21Z",
  "producer": "order-service",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "correlationId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "data": { }
}
```

| Field | What it is for |
|---|---|
| `eventId` | Deduplication key — this is what goes into `processed_events` |
| `eventType` | Discriminator; determines the shape of `data` |
| `eventVersion` | Payload version; bumped on a contract-breaking change |
| `occurredAt` | When the fact happened, not when it was published |
| `traceId` | OpenTelemetry trace, propagated end to end |
| `correlationId` | Usually the `orderId`; makes raw messages readable in the Kafka UI |

Watch out for one naming trap: `envelope.eventId` is the **message id**, while
`data.eventId` inside `ORDER_CREATED` is the **show id**. They are different things.

## The three events

### `ORDER_CREATED`

`order-service` → `ticketflow.orders.created`, written to the outbox in the same
transaction as the order. Carries everything the Payment Service needs to charge.

**It carries no card data.** Payment credentials go from the customer straight to the
provider; they never travel through Kafka and never appear in a log.

### `PAGAMENTO_APROVADO` / `PAGAMENTO_RECUSADO`

`payment-service` → `ticketflow.payments.processed`, after the gateway responds.

Both share a single topic. Splitting them into two topics would lose the ordering
guarantee between them, and whoever cares about one almost always cares about the
other — `eventType` already tells them apart.

`PAGAMENTO_APROVADO` requires `gatewayTransactionId`; `PAGAMENTO_RECUSADO` requires
`failureCode`. That lives in the JSON Schema, not only in convention.

> These two names are in Portuguese because they are fixed project vocabulary. All the
> rest of the code is in English. It is the only exception, and it is deliberate.

## Retry and DLQ

The rule in any consumer:

1. **Transient error** (gateway down, database unavailable) → retry with exponential
   backoff.
2. **Permanent error** (invalid payload, event for an order that does not exist) →
   straight to the DLQ. Retrying will not fix it.
3. **Attempts exhausted** → DLQ, with the error in the headers.

Never swallow the exception and commit the offset: the message vanishes and nobody
knows.

## Schema compatibility

The rule when evolving a payload:

- A new **optional** field → compatible, does not bump `eventVersion`.
- A new required field, a removed field, or a changed type → **breaking**. Bump
  `eventVersion`, and the consumer reads both versions until the producer stops
  emitting the old one.

Never reuse a field name with a different meaning — that is the kind of change that
passes every test and breaks in production.
