-- =============================================================================
-- Payment Service - initial schema
-- Database: ticketflow_payments (PostgreSQL)
--
-- Separate database from the Order Service on purpose: each microservice owns
-- its data and they only ever talk through Kafka. No cross-database joins,
-- no foreign key from payments.order_id to orders.id.
-- =============================================================================

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- -----------------------------------------------------------------------------
-- payments - one row per order. The UNIQUE on order_id is the last line of
-- defence against charging the same order twice if an ORDER_CREATED event is
-- redelivered and the inbox check somehow races.
-- -----------------------------------------------------------------------------
CREATE TABLE payments (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id               UUID          NOT NULL,
    customer_id            UUID          NOT NULL,
    amount                 NUMERIC(12,2) NOT NULL,
    currency               CHAR(3)       NOT NULL DEFAULT 'BRL',
    method                 VARCHAR(20)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    gateway_name           VARCHAR(60),
    gateway_transaction_id VARCHAR(120),
    failure_code           VARCHAR(60),
    failure_reason         VARCHAR(255),
    attempts               INTEGER       NOT NULL DEFAULT 0,
    authorized_at          TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_payments_order UNIQUE (order_id),
    CONSTRAINT ck_payments_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'FAILED')
    ),
    CONSTRAINT ck_payments_method CHECK (
        method IN ('CREDIT_CARD', 'PIX', 'BOLETO')
    ),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0),
    -- An approved payment must carry the gateway's transaction id.
    CONSTRAINT ck_payments_approved_has_tx CHECK (
        status <> 'APPROVED' OR gateway_transaction_id IS NOT NULL
    )
);

CREATE UNIQUE INDEX ux_payments_gateway_transaction
    ON payments (gateway_name, gateway_transaction_id)
    WHERE gateway_transaction_id IS NOT NULL;

CREATE INDEX ix_payments_status_created ON payments (status, created_at);

CREATE TRIGGER tg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON COLUMN payments.method IS
    'Selects the PaymentStrategy implementation. Adding a method must not add an if/else - add a strategy bean.';
COMMENT ON COLUMN payments.status IS
    'REJECTED = gateway answered "no". FAILED = gateway never gave a usable answer (timeout, 5xx).';


-- -----------------------------------------------------------------------------
-- payment_attempts - one row per call to the external gateway.
-- This is what makes the Wiremock integration tests observable: the timeout
-- and 5xx scenarios must produce attempt rows with outcome TIMEOUT / ERROR,
-- not just a failed assertion.
-- -----------------------------------------------------------------------------
CREATE TABLE payment_attempts (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id       UUID        NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    attempt_number   INTEGER     NOT NULL,
    outcome          VARCHAR(20) NOT NULL,
    http_status      INTEGER,
    latency_ms       INTEGER,
    request_payload  JSONB,
    response_payload JSONB,
    error_message    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_payment_attempts_number UNIQUE (payment_id, attempt_number),
    CONSTRAINT ck_payment_attempts_outcome CHECK (
        outcome IN ('APPROVED', 'REJECTED', 'TIMEOUT', 'ERROR')
    ),
    CONSTRAINT ck_payment_attempts_number CHECK (attempt_number > 0)
);

CREATE INDEX ix_payment_attempts_payment ON payment_attempts (payment_id, attempt_number);

COMMENT ON COLUMN payment_attempts.request_payload IS
    'Masked request. Card number, CVV and expiry must never be persisted - store only the last 4 digits and the brand.';


-- -----------------------------------------------------------------------------
-- processed_events - inbox / idempotent consumer for ORDER_CREATED.
-- -----------------------------------------------------------------------------
CREATE TABLE processed_events (
    event_id       UUID         NOT NULL,
    consumer_group VARCHAR(120) NOT NULL,
    topic          VARCHAR(120) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_group)
);

CREATE INDEX ix_processed_events_processed_at ON processed_events (processed_at);


-- -----------------------------------------------------------------------------
-- outbox_messages - transactional outbox for PAGAMENTO_APROVADO /
-- PAGAMENTO_RECUSADO. Same shape as the Order Service outbox.
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

CREATE INDEX ix_outbox_dispatchable
    ON outbox_messages (available_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_outbox_aggregate ON outbox_messages (aggregate_type, aggregate_id);
