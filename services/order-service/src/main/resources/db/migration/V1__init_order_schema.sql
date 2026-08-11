-- =============================================================================
-- Order Service - initial schema
-- Database: ticketflow_orders (PostgreSQL)
--
-- Owns the transactional side of the purchase flow: the event catalogue,
-- the orders themselves and the transactional outbox used to publish
-- ORDER_CREATED to Kafka atomically with the order insert.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Shared helper: keeps updated_at in sync on every UPDATE.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- -----------------------------------------------------------------------------
-- events - the catalogue a customer browses before ordering.
-- -----------------------------------------------------------------------------
CREATE TABLE events (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(180) NOT NULL,
    description    TEXT,
    venue          VARCHAR(180) NOT NULL,
    city           VARCHAR(120) NOT NULL,
    starts_at      TIMESTAMPTZ  NOT NULL,
    sales_start_at TIMESTAMPTZ  NOT NULL,
    sales_end_at   TIMESTAMPTZ  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_events_status
        CHECK (status IN ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'CANCELLED', 'FINISHED')),
    CONSTRAINT ck_events_sales_window
        CHECK (sales_end_at > sales_start_at)
);

CREATE INDEX ix_events_status_starts_at ON events (status, starts_at);
CREATE INDEX ix_events_city             ON events (city);

CREATE TRIGGER tg_events_updated_at
    BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- -----------------------------------------------------------------------------
-- ticket_categories - price tiers within an event (Pista, VIP, Camarote...).
-- Inventory counters live here; `version` backs JPA optimistic locking so two
-- concurrent orders cannot oversell the same tier.
-- -----------------------------------------------------------------------------
CREATE TABLE ticket_categories (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id          UUID         NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    name              VARCHAR(80)  NOT NULL,
    price_amount      NUMERIC(12,2) NOT NULL,
    currency          CHAR(3)      NOT NULL DEFAULT 'BRL',
    total_quantity    INTEGER      NOT NULL,
    reserved_quantity INTEGER      NOT NULL DEFAULT 0,
    sold_quantity     INTEGER      NOT NULL DEFAULT 0,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_ticket_categories_event_name UNIQUE (event_id, name),
    CONSTRAINT ck_ticket_categories_price      CHECK (price_amount >= 0),
    CONSTRAINT ck_ticket_categories_totals     CHECK (total_quantity > 0),
    CONSTRAINT ck_ticket_categories_counters   CHECK (
        reserved_quantity >= 0
        AND sold_quantity >= 0
        AND reserved_quantity + sold_quantity <= total_quantity
    )
);

CREATE INDEX ix_ticket_categories_event ON ticket_categories (event_id);

CREATE TRIGGER tg_ticket_categories_updated_at
    BEFORE UPDATE ON ticket_categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- -----------------------------------------------------------------------------
-- orders - created as PENDING and answered to the caller immediately (202).
-- The final status only arrives later, via the payment result consumed
-- from Kafka. No synchronous call to Payment Service ever happens here.
-- -----------------------------------------------------------------------------
CREATE TABLE orders (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(80)   NOT NULL,
    customer_id     UUID          NOT NULL,
    customer_name   VARCHAR(180)  NOT NULL,
    customer_email  VARCHAR(180)  NOT NULL,
    event_id        UUID          NOT NULL REFERENCES events (id),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    payment_method  VARCHAR(20)   NOT NULL,
    total_amount    NUMERIC(12,2) NOT NULL,
    currency        CHAR(3)       NOT NULL DEFAULT 'BRL',
    expires_at      TIMESTAMPTZ,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_orders_status CHECK (
        status IN ('PENDING', 'PAID', 'REJECTED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT ck_orders_payment_method CHECK (
        payment_method IN ('CREDIT_CARD', 'PIX', 'BOLETO')
    ),
    CONSTRAINT ck_orders_total CHECK (total_amount >= 0)
);

CREATE INDEX ix_orders_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX ix_orders_event            ON orders (event_id);
CREATE INDEX ix_orders_status           ON orders (status) WHERE status = 'PENDING';

CREATE TRIGGER tg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN orders.idempotency_key IS
    'Client-supplied Idempotency-Key header. A retried POST /orders returns the original order instead of creating a duplicate.';


-- -----------------------------------------------------------------------------
-- order_items - one line per ticket category bought in the order.
-- unit_price is copied (not joined) so a later price change never rewrites
-- the history of an order already placed.
-- -----------------------------------------------------------------------------
CREATE TABLE order_items (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id           UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    ticket_category_id UUID          NOT NULL REFERENCES ticket_categories (id),
    category_name      VARCHAR(80)   NOT NULL,
    quantity           INTEGER       NOT NULL,
    unit_price         NUMERIC(12,2) NOT NULL,
    subtotal           NUMERIC(12,2) NOT NULL,

    CONSTRAINT uq_order_items_order_category UNIQUE (order_id, ticket_category_id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0 AND quantity <= 10),
    CONSTRAINT ck_order_items_prices   CHECK (unit_price >= 0 AND subtotal >= 0)
);

CREATE INDEX ix_order_items_order ON order_items (order_id);


-- -----------------------------------------------------------------------------
-- order_status_history - append-only audit trail. Feeds the "acompanhe seu
-- pedido" timeline on the React front-end (phase 4).
-- -----------------------------------------------------------------------------
CREATE TABLE order_status_history (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20) NOT NULL,
    reason          VARCHAR(255),
    source_event_id UUID,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_order_status_history_order ON order_status_history (order_id, occurred_at);

COMMENT ON COLUMN order_status_history.source_event_id IS
    'eventId of the Kafka message that caused the transition, for end-to-end tracing.';


-- -----------------------------------------------------------------------------
-- outbox_messages - transactional outbox.
-- The order INSERT and the event INSERT share one transaction, so an order can
-- never exist without its event (and vice-versa). A relay publishes rows to
-- Kafka and marks them PUBLISHED.
-- -----------------------------------------------------------------------------
CREATE TABLE outbox_messages (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(60)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    topic          VARCHAR(120) NOT NULL,
    partition_key  VARCHAR(120) NOT NULL,
    payload        JSONB        NOT NULL,
    headers        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     TEXT,
    available_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,

    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Partial index: the relay only ever scans rows still waiting to be published.
CREATE INDEX ix_outbox_dispatchable
    ON outbox_messages (available_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_outbox_aggregate ON outbox_messages (aggregate_type, aggregate_id);


-- -----------------------------------------------------------------------------
-- processed_events - inbox / idempotent consumer.
-- Order Service also *consumes* the payment result to move PENDING -> PAID or
-- REJECTED. Kafka gives at-least-once delivery, so a redelivered event must
-- not apply the transition twice.
-- -----------------------------------------------------------------------------
CREATE TABLE processed_events (
    event_id       UUID         NOT NULL,
    consumer_group VARCHAR(120) NOT NULL,
    topic          VARCHAR(120) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX ix_processed_events_processed_at ON processed_events (processed_at);
