import { useEffect, useState } from 'react';
import { cancelOrder, listTickets, ProblemError, type Order, type OrderStatus, type Ticket } from '../api';
import { CardPayment } from '../components/CardPayment';
import { Countdown } from '../components/Countdown';
import { Icon } from '../components/Icon';
import { Stepper } from '../components/Stepper';
import { TicketList } from '../components/TicketList';
import { dateTime, money } from '../lib/format';
import { linkProps } from '../lib/router';
import { useRefundStatus } from '../useRefundStatus';

interface Props {
  order: Order | null;
  error: string | null;
}

const METHOD_LABEL: Record<string, string> = {
  CREDIT_CARD: 'Cartão de crédito',
  PIX: 'PIX',
  BOLETO: 'Boleto bancário',
};

/**
 * `label` vai no selo, `headline` no título. São textos diferentes de propósito:
 * repetir a mesma frase duas vezes na mesma tela é ruído, não ênfase.
 */
const STATUS: Record<OrderStatus, { label: string; headline: string; hint: string; tone: string }> = {
  PENDING: {
    label: 'Aguardando pagamento',
    headline: 'Pedido confirmado!',
    hint: 'Já reservamos seus ingressos. Estamos processando a cobrança e esta tela se atualiza sozinha.',
    tone: 'pending',
  },
  PAID: {
    label: 'Pago',
    headline: 'Tudo certo, ingressos garantidos',
    hint: 'O pagamento foi aprovado e seus ingressos foram emitidos.',
    tone: 'paid',
  },
  REJECTED: {
    label: 'Recusado',
    headline: 'Não foi dessa vez',
    hint: 'A operadora não autorizou a cobrança. Seus ingressos voltaram para o estoque.',
    tone: 'rejected',
  },
  CANCELLED: {
    label: 'Cancelado',
    headline: 'Pedido cancelado',
    hint: 'Este pedido foi cancelado.',
    tone: 'rejected',
  },
  EXPIRED: {
    label: 'Expirado',
    headline: 'O prazo acabou',
    hint: 'O pagamento não chegou a tempo e os ingressos voltaram para o estoque.',
    tone: 'rejected',
  },
};

export function OrderPage({ order, error }: Props) {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const paid = order?.status === 'PAID';

  // Só faz sentido perguntar pelo dinheiro depois que o pedido acabou sem
  // pagamento. Antes disso a cobrança ainda é a do fluxo normal.
  const cancelled = order?.status === 'CANCELLED';
  const {
    payment: refund,
    pending: refundPending,
    unknown: refundUnknown,
  } = useRefundStatus(order?.id ?? null, cancelled);

  /**
   * Cancelar não pede confirmação por rigor de processo, e sim porque é
   * irreversível: o pedido não volta, e os ingressos podem ter acabado no
   * intervalo.
   *
   * O 409 é tratado como sucesso silencioso de propósito. Ele significa que o
   * pedido já tinha acabado — quase sempre porque o pagamento chegou enquanto a
   * pessoa decidia. Mostrar "erro" aí seria culpar o cliente por uma corrida do
   * sistema; a tela se atualiza sozinha e o estado novo aparece.
   */
  async function handleCancel(orderId: string) {
    setCancelling(true);
    setCancelError(null);
    try {
      await cancelOrder(orderId);
      setConfirming(false);
    } catch (e) {
      if (e instanceof ProblemError && e.status === 409) {
        setConfirming(false);
      } else {
        setCancelError(e instanceof Error ? e.message : 'Não foi possível cancelar');
      }
    } finally {
      setCancelling(false);
    }
  }

  // Os ingressos só existem depois que o pagamento é aprovado, e são emitidos por
  // outro serviço — buscar antes disso seria pedir algo que ainda não foi criado.
  useEffect(() => {
    if (!paid || !order) return;
    let cancelled = false;
    listTickets(order.id)
      .then((page) => !cancelled && setTickets(page.content))
      .catch(() => {
        /* o pedido está pago de qualquer forma; a falta do ingresso na tela não
           deve virar erro em cima de uma compra bem-sucedida */
      });
    return () => {
      cancelled = true;
    };
  }, [paid, order?.id]);

  if (!order) {
    return (
      <section className="shell section narrow">
        {error ? (
          <p className="alert">
            <Icon name="close" size={18} />
            {error}
          </p>
        ) : (
          <div className="stack">
            <div className="skeleton" style={{ height: 260, borderRadius: 'var(--radius-lg)' }} />
          </div>
        )}
      </section>
    );
  }

  const status = STATUS[order.status];

  return (
    <section className="shell section narrow">
      <Stepper current={3} />

      {/* O canhoto: a metade de cima é o pedido, a de baixo o acompanhamento,
          separadas pela linha picotada — é a forma que todo mundo reconhece. */}
      <article className="stub">
        <div className="stub-top">
          <div className="stub-head">
            <span className="stub-code">PEDIDO {order.id.slice(0, 8).toUpperCase()}</span>
            <span className={`pill pill-${status.tone}`}>
              {order.status === 'PENDING' && <span className="dot" aria-hidden="true" />}
              {status.label}
            </span>
          </div>

          <div>
            <h1>{status.headline}</h1>
            <p className="muted" style={{ marginTop: 'var(--space-2)' }}>
              {status.hint}
            </p>
          </div>

          <ul className="stub-items">
            {order.items.map((item, i) => (
              <li key={i}>
                <span>
                  <strong>{item.quantity}×</strong> {item.categoryName}
                </span>
                <span>{money(item.subtotal)}</span>
              </li>
            ))}
          </ul>

          <div className="stub-total">
            <div>
              <span>Total</span>
              <div className="muted small" style={{ fontWeight: 400 }}>
                {METHOD_LABEL[order.paymentMethod] ?? order.paymentMethod}
              </div>
            </div>
            <strong>{money(order.totalAmount)}</strong>
          </div>

          {/* Cancelado sozinho não diz se alguém foi cobrado — e é a primeira
              coisa que quem cancelou quer saber. Os três desfechos vêm do status
              da cobrança, que mora no Payment Service. */}
          {cancelled && (
            <div className="refund-note">
              <Icon name={refund?.refunded ? 'check' : 'clock'} size={16} />
              <span>
                {/* `refunded` vem antes do status de propósito: numa cobrança que
                    cruzou o cancelamento em voo o dinheiro volta e o status fica
                    CANCELLED, e olhar só o rótulo diria "nada foi cobrado" para
                    quem foi cobrado. */}
                {refundUnknown ? (
                  <>Não foi possível confirmar agora o que houve com a cobrança.</>
                ) : refund?.refunded ? (
                  <>
                    <strong>{money(refund.amount)}</strong> estornados para a forma de
                    pagamento original. O prazo de aparecer na fatura é da operadora.
                  </>
                ) : !refund || refund.status === 'CANCELLED' ? (
                  <>Nenhuma cobrança foi feita.</>
                ) : refundPending ? (
                  <>Processando o estorno de {money(refund.amount)}…</>
                ) : (
                  // Cobrança que ficou APPROVED sem virar estorno: o provedor
                  // recusou a devolução. Dizer "estornado" aqui seria mentir.
                  <>
                    A cobrança de {money(refund.amount)} ainda consta como paga. Fale com
                    o suporte informando o número do pedido.
                  </>
                )}
              </span>
            </div>
          )}

          {/* O relógio só importa enquanto o pagamento pode chegar. */}
          {order.status === 'PENDING' && order.expiresAt && (
            <div className="deadline">
              <Countdown deadline={order.expiresAt} />
              <span className="muted small">
                Depois disso os ingressos voltam para o estoque.
              </span>
            </div>
          )}

          {/* Desistir só faz sentido enquanto ninguém foi cobrado. Depois de
              pago o caminho é outro — estorno —, com outras regras. */}
          {order.status === 'PENDING' && (
            <div className="cancel-area">
              {confirming ? (
                <div className="stack-sm">
                  <p className="muted small">
                    Cancelar libera seus ingressos para outras pessoas, e isso não
                    tem volta. Se a cobrança já tiver saído, o valor é devolvido.
                  </p>
                  <div className="row">
                    <button
                      className="btn btn-danger"
                      onClick={() => handleCancel(order.id)}
                      disabled={cancelling}
                    >
                      {cancelling ? 'Cancelando…' : 'Sim, cancelar pedido'}
                    </button>
                    <button
                      className="btn btn-ghost"
                      onClick={() => setConfirming(false)}
                      disabled={cancelling}
                    >
                      Manter pedido
                    </button>
                  </div>
                </div>
              ) : (
                <button className="btn btn-ghost btn-sm" onClick={() => setConfirming(true)}>
                  <Icon name="close" size={15} />
                  Cancelar pedido
                </button>
              )}

              {cancelError && (
                <p className="alert" style={{ marginTop: 'var(--space-3)' }}>
                  <Icon name="close" size={16} />
                  {cancelError}
                </p>
              )}
            </div>
          )}
        </div>

        {/* Cartão é o único método que precisa de uma ação aqui: PIX e boleto
            se resolvem fora do site, e a resposta chega por webhook. */}
        {order.status === 'PENDING' && order.paymentMethod === 'CREDIT_CARD' && (
          <CardPayment orderId={order.id} />
        )}

        <div className="perforation" aria-hidden="true">
          <span className="notch left" />
          <span className="dashes" />
          <span className="notch right" />
        </div>

        <div className="stub-bottom">
          <TicketList tickets={tickets} />

          <div>
            <h3>Acompanhamento</h3>
            <ol className="timeline">
              {order.statusHistory.map((change, i) => {
                const isLast = i === order.statusHistory.length - 1;
                return (
                  <li key={i} className={isLast ? 'current' : ''}>
                    <span className="bullet" aria-hidden="true" />
                    <div>
                      <strong>{STATUS[change.toStatus].label}</strong>
                      <span className="muted small"> · {dateTime(change.occurredAt)}</span>
                      {change.reason && <div className="reason">{change.reason}</div>}
                    </div>
                  </li>
                );
              })}
              {order.status === 'PENDING' && (
                <li className="waiting">
                  <span className="bullet pulsing" aria-hidden="true" />
                  <div className="muted">Aguardando resposta do pagamento…</div>
                </li>
              )}
            </ol>
          </div>
        </div>
      </article>

      {error && (
        <p className="note" style={{ marginTop: 'var(--space-4)' }}>
          <Icon name="clock" size={16} />
          {error}
        </p>
      )}

      <div className="row" style={{ justifyContent: 'center', marginTop: 'var(--space-6)' }}>
        <a className="btn btn-secondary" {...linkProps('/events')}>
          Ver outros eventos
        </a>
        <a className="btn btn-ghost" {...linkProps('/orders')}>
          Meus pedidos
        </a>
      </div>
    </section>
  );
}
