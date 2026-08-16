-- =============================================================================
-- V3 - A cobrança passa a saber ser cancelada e estornada.
--
-- É a outra metade do cancelamento de pedido. O Order Service já publica
-- ORDER_CANCELLED; aqui a cobrança ganha os dois desfechos que esse evento pode
-- produzir, e eles são coisas diferentes:
--
--   CANCELLED - o pedido acabou antes de o dinheiro sair. Nada foi cobrado e
--               nada será. Também é a linha que o Payment Service grava quando
--               o cancelamento chega ANTES do ORDER_CREATED: a cobrança nasce
--               morta e o consumidor do pedido a encontra pronta, por causa do
--               UNIQUE (order_id), em vez de cobrar o cartão de quem desistiu.
--
--   REFUNDED  - foi cobrado e devolvido. Só se chega aqui a partir de APPROVED.
--
-- `refund_id` é o comprovante. Sem ele "estornei" é afirmação sem prova, e é
-- exatamente isso que uma disputa derruba — por isso a restrição exige o id
-- sempre que o status for REFUNDED.
-- =============================================================================

ALTER TABLE payments DROP CONSTRAINT ck_payments_status;

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'FAILED', 'CANCELLED', 'REFUNDED')
    );

ALTER TABLE payments ADD COLUMN refund_id VARCHAR(120);

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_refunded_has_refund_id CHECK (
        status <> 'REFUNDED' OR refund_id IS NOT NULL
    );

-- Uma cobrança estornada continua carregando a transação original: é ela que
-- liga o estorno ao que saiu. Por isso ck_payments_approved_has_tx nao serve
-- como esta - o status ja nao e APPROVED - e a regra e escrita aqui.
ALTER TABLE payments
    ADD CONSTRAINT ck_payments_refunded_keeps_charge CHECK (
        status <> 'REFUNDED' OR gateway_transaction_id IS NOT NULL
    );

COMMENT ON COLUMN payments.refund_id IS
    'Provider id for the refund. Present only on REFUNDED, and the proof the money went back.';
