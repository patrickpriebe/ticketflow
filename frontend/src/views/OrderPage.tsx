import { useEffect, useState } from 'react';
import { listTickets, type Order, type OrderStatus, type Ticket } from '../api';
import { Countdown } from '../components/Countdown';
import { Icon } from '../components/Icon';
import { Stepper } from '../components/Stepper';
import { TicketList } from '../components/TicketList';
import { dateTime, money } from '../lib/format';
import { linkProps } from '../lib/router';

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
  const paid = order?.status === 'PAID';

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

          {/* O relógio só importa enquanto o pagamento pode chegar. */}
          {order.status === 'PENDING' && order.expiresAt && (
            <div className="deadline">
              <Countdown deadline={order.expiresAt} />
              <span className="muted small">
                Depois disso os ingressos voltam para o estoque.
              </span>
            </div>
          )}
        </div>

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
