-- =============================================================================
-- Inbox dos webhooks do provedor de pagamento.
--
-- Por que uma tabela separada de `processed_events`, e não uma linha a mais lá:
--
--   1. `processed_events.event_id` é UUID, e o identificador do Stripe é texto
--      (`evt_3Abc...`). Caberia derivar um UUID do texto, como fizemos com a
--      identidade do cliente, mas ali havia um motivo — o domínio usa UUID. Aqui
--      não há: o id é do provedor e serve para conversar com ele.
--
--   2. São duas fontes de repetição diferentes. O Kafka reentrega porque a
--      entrega é at-least-once; o Stripe reenvia porque não recebeu 200 nosso.
--      Misturar as duas na mesma tabela faria uma varredura de diagnóstico
--      responder a pergunta errada.
--
-- O provedor entra na chave porque o mesmo id de evento pode existir em dois
-- provedores diferentes, e um dia haverá outro.
-- =============================================================================

CREATE TABLE payment_webhook_events (
    provider      VARCHAR(40)   NOT NULL,
    event_id      VARCHAR(120)  NOT NULL,
    event_type    VARCHAR(80)   NOT NULL,
    payment_id    UUID,
    received_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_payment_webhook_events PRIMARY KEY (provider, event_id),
    CONSTRAINT fk_payment_webhook_events_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id)
);

-- Para a pergunta "o que chegou nos últimos minutos", que é a primeira coisa que
-- se faz quando um pagamento não resolve.
CREATE INDEX ix_payment_webhook_events_received ON payment_webhook_events (received_at DESC);

-- Para reconciliação: todos os webhooks de um pagamento, na ordem.
CREATE INDEX ix_payment_webhook_events_payment ON payment_webhook_events (payment_id, received_at);

COMMENT ON TABLE payment_webhook_events IS
    'Idempotência dos webhooks: o provedor reenvia até receber 200, e o mesmo evento não pode liquidar o pagamento duas vezes.';
