import { useEffect, useState } from 'react';
import { listMyOrders, ProblemError, type Order, type OrderStatus } from '../api';
import { Icon } from '../components/Icon';
import { dateTime, money } from '../lib/format';
import { linkProps, navigate } from '../lib/router';

/** Os filtros que o backend aceita em `GET /orders?status=`. */
const TABS: { value: OrderStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Todos' },
  { value: 'PENDING', label: 'Aguardando' },
  { value: 'PAID', label: 'Pagos' },
  { value: 'REJECTED', label: 'Recusados' },
  { value: 'EXPIRED', label: 'Expirados' },
];

const TONE: Record<OrderStatus, string> = {
  PENDING: 'pending',
  PAID: 'paid',
  REJECTED: 'rejected',
  CANCELLED: 'rejected',
  EXPIRED: 'rejected',
};

const LABEL: Record<OrderStatus, string> = {
  PENDING: 'Aguardando',
  PAID: 'Pago',
  REJECTED: 'Recusado',
  CANCELLED: 'Cancelado',
  EXPIRED: 'Expirado',
};

export function MyOrders() {
  const [filter, setFilter] = useState<OrderStatus | 'ALL'>('ALL');
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // O filtro vai para o backend, não para um `.filter()` aqui: a lista é paginada,
  // e filtrar depois de receber a página esconderia os pedidos das páginas seguintes.
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    listMyOrders(filter === 'ALL' ? undefined : filter)
      .then((page) => !cancelled && setOrders(page.content))
      .catch((e) => {
        if (cancelled) return;
        setError(e instanceof ProblemError ? e.detail : 'Não foi possível carregar seus pedidos.');
      })
      .finally(() => !cancelled && setLoading(false));

    return () => {
      cancelled = true;
    };
  }, [filter]);

  return (
    <section className="shell section narrow">
      <div className="section-head">
        <div>
          <h1>Meus pedidos</h1>
          <p>Acompanhe o status e abra os ingressos já emitidos.</p>
        </div>
      </div>

      <div className="tabs" role="tablist">
        {TABS.map((tab) => (
          <button
            key={tab.value}
            role="tab"
            aria-selected={filter === tab.value}
            className={`tab${filter === tab.value ? ' active' : ''}`}
            onClick={() => setFilter(tab.value)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {error && (
        <p className="alert">
          <Icon name="close" size={18} />
          {error}
        </p>
      )}

      {loading ? (
        <div className="stack">
          {[0, 1, 2].map((i) => (
            <div key={i} className="skeleton" style={{ height: 76, borderRadius: 'var(--radius-md)' }} />
          ))}
        </div>
      ) : orders.length === 0 ? (
        <div className="empty">
          <Icon name="inbox" size={28} />
          <div>
            <strong>
              {filter === 'ALL' ? 'Você ainda não fez nenhum pedido.' : 'Nenhum pedido com esse status.'}
            </strong>
          </div>
          <a className="btn btn-primary btn-sm" {...linkProps('/events')}>
            Descobrir eventos
          </a>
        </div>
      ) : (
        <ul className="stack">
          {orders.map((order) => (
            <li key={order.id}>
              <button
                className="order-row"
                aria-label={`Abrir pedido ${order.id.slice(0, 8).toUpperCase()}`}
                onClick={() => navigate(`/orders/${order.id}`)}
              >
                <span className="order-row-main">
                  <strong>{order.items.map((item) => `${item.quantity}× ${item.categoryName}`).join(', ')}</strong>
                  <span className="muted small">
                    {money(order.totalAmount)} · {dateTime(order.createdAt)}
                  </span>
                </span>
                <span className={`pill pill-${TONE[order.status]}`}>{LABEL[order.status]}</span>
                <Icon name="chevron" size={16} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
