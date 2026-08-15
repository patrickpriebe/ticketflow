-- =============================================================================
-- V2 - The idempotency key belongs to a customer, not to the whole table.
--
-- V1 made `idempotency_key` unique across every order in the system. That looked
-- like a stricter rule, and it was the opposite: it turned a header the client
-- chooses into a way to read someone else's order.
--
-- The path was short. `POST /orders` replays when the key already exists, and it
-- replayed whoever owned it - so sending `Idempotency-Key: order-1`, a value
-- anyone would try, answered 200 with the other customer's order: their name,
-- their e-mail, what they bought and what they paid. No token of theirs, no
-- probing of ids, nothing that looked like an attack in the logs.
--
-- Scoping the key to the customer fixes both halves at once. Two people using
-- "order-1" is not a conflict and stops being treated as one, and a key can only
-- ever replay an order of whoever sent it.
--
-- Safe on existing data: (customer_id, idempotency_key) is unique wherever
-- idempotency_key alone already was, so this constraint cannot fail on rows that
-- V1 accepted.
-- =============================================================================

ALTER TABLE orders DROP CONSTRAINT uq_orders_idempotency_key;

ALTER TABLE orders
    ADD CONSTRAINT uq_orders_customer_idempotency_key
        UNIQUE (customer_id, idempotency_key);

COMMENT ON COLUMN orders.idempotency_key IS
    'Client-supplied Idempotency-Key header, unique per customer. A retried POST /orders returns that customer''s original order; the same key sent by someone else is a different order, never a replay of theirs.';
