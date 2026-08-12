import type { Order, OrderStatus } from '../api';
import { dateTime, money } from '../lib/format';

interface Props {
  order: Order | null;
  error: string | null;
  onBack: () => void;
}

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
    hint: 'A operadora do cartão não autorizou a cobrança. Seus ingressos voltaram para o estoque.',
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

export function OrderPage({ order, error, onBack }: Props) {
  if (!order) {
    return (
      <section className="shell section">
        {error ? <p className="alert">{error}</p> : <p className="muted">Carregando pedido…</p>}
      </section>
    );
  }

  const status = STATUS[order.status];

  return (
    <section className="shell section narrow">
      <button className="back dark" onClick={onBack}>
        ← Voltar para os eventos
      </button>

      {/* O canhoto: a metade de cima é o pedido, a de baixo o acompanhamento,
          separadas pela linha picotada — é a forma que todo mundo reconhece. */}
      <article className={`stub tone-${status.tone}`}>
        <div className="stub-top">
          <div className="stub-head">
            <span className="muted small">Pedido {order.id.slice(0, 8).toUpperCase()}</span>
            <span className={`pill pill-${status.tone}`}>
              {order.status === 'PENDING' && <span className="dot" aria-hidden="true" />}
              {status.label}
            </span>
          </div>

          <h2>{status.headline}</h2>
          <p className="muted">{status.hint}</p>

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
            <span>Total</span>
            <strong>{money(order.totalAmount)}</strong>
          </div>
        </div>

        <div className="perforation" aria-hidden="true">
          <span className="notch left" />
          <span className="dashes" />
          <span className="notch right" />
        </div>

        <div className="stub-bottom">
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
      </article>

      {error && <p className="alert soft">{error}</p>}
    </section>
  );
}
