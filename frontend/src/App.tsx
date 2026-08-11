import { useEffect, useState } from 'react';
import {
  getEvent,
  listEvents,
  placeOrder,
  ProblemError,
  type EventDetail,
  type EventSummary,
  type OrderStatus,
} from './api';
import { useOrderStatus } from './useOrderStatus';

// Cliente fixo porque a API ainda não tem autenticação — a identidade chega no
// corpo. Está listado como dívida conhecida no README; um front real leria isso
// do token.
const CUSTOMER = {
  id: '3f1c9a6e-77b2-4c0d-9f31-2a5b8e4d6c10',
  name: 'Ana Souza',
  email: 'ana.souza@example.com',
};

const money = (value: { amount: number; currency: string }) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: value.currency }).format(value.amount);

const dateTime = (iso: string) =>
  new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso));

const STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: 'Aguardando pagamento',
  PAID: 'Pago',
  REJECTED: 'Pagamento recusado',
  CANCELLED: 'Cancelado',
  EXPIRED: 'Expirado',
};

export default function App() {
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [selected, setSelected] = useState<EventDetail | null>(null);
  const [orderId, setOrderId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [placing, setPlacing] = useState(false);

  const { order, error: pollError } = useOrderStatus(orderId);

  useEffect(() => {
    listEvents()
      .then((page) => setEvents(page.content))
      .catch((e) => setError(e instanceof Error ? e.message : 'Falha ao carregar eventos'));
  }, []);

  const openEvent = async (id: string) => {
    setError(null);
    try {
      setSelected(await getEvent(id));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha ao carregar o evento');
    }
  };

  const buy = async (ticketCategoryId: string, quantity: number) => {
    if (!selected) return;
    setPlacing(true);
    setError(null);
    try {
      const created = await placeOrder({
        eventId: selected.id,
        ticketCategoryId,
        quantity,
        customer: CUSTOMER,
      });
      setOrderId(created.id);
      setSelected(null);
    } catch (e) {
      // Um 409 de estoque insuficiente chega aqui com o texto do backend, que já
      // diz quantos ingressos restam.
      setError(e instanceof ProblemError ? `${e.title}: ${e.detail}` : 'Falha ao criar o pedido');
    } finally {
      setPlacing(false);
    }
  };

  const reset = () => {
    setOrderId(null);
    setSelected(null);
    setError(null);
  };

  return (
    <main>
      <header>
        <h1>TicketFlow</h1>
        <p className="lede">
          O pedido é aceito sem esperar o pagamento. Repare que o status abaixo começa
          em <strong>aguardando</strong> e muda sozinho — nada nesta tela ficou bloqueado
          esperando o gateway.
        </p>
      </header>

      {error && <p className="error">{error}</p>}

      {orderId ? (
        <section className="card">
          <h2>Seu pedido</h2>
          {pollError && <p className="error">{pollError}</p>}
          {!order ? (
            <p className="muted">Carregando…</p>
          ) : (
            <>
              <p className={`status status-${order.status.toLowerCase()}`}>
                {STATUS_LABEL[order.status]}
                {order.status === 'PENDING' && <span className="pulse" aria-hidden="true" />}
              </p>
              <p className="muted">
                {order.items.map((i) => `${i.quantity}x ${i.categoryName}`).join(', ')} ·{' '}
                {money(order.totalAmount)}
              </p>

              <ol className="timeline">
                {order.statusHistory.map((change, i) => (
                  <li key={i}>
                    <strong>{STATUS_LABEL[change.toStatus]}</strong>
                    <span className="muted"> · {dateTime(change.occurredAt)}</span>
                    {change.reason && <div className="reason">{change.reason}</div>}
                  </li>
                ))}
              </ol>

              <button onClick={reset}>Fazer outro pedido</button>
            </>
          )}
        </section>
      ) : selected ? (
        <section className="card">
          <button className="link" onClick={() => setSelected(null)}>
            ← voltar
          </button>
          <h2>{selected.name}</h2>
          <p className="muted">
            {selected.venue} · {selected.city} · {dateTime(selected.startsAt)}
          </p>

          <ul className="categories">
            {selected.categories.map((category) => (
              <li key={category.id}>
                <div>
                  <strong>{category.name}</strong>
                  <span className="muted"> · {money(category.price)}</span>
                  <div className="muted small">{category.availableQuantity} disponíveis</div>
                </div>
                <div className="actions">
                  {[1, 2].map((quantity) => (
                    <button
                      key={quantity}
                      disabled={placing || category.availableQuantity < quantity}
                      onClick={() => buy(category.id, quantity)}
                    >
                      comprar {quantity}
                    </button>
                  ))}
                </div>
              </li>
            ))}
          </ul>
        </section>
      ) : (
        <section className="grid">
          {events.map((event) => (
            <button key={event.id} className="card event" onClick={() => openEvent(event.id)}>
              <strong>{event.name}</strong>
              <span className="muted">
                {event.venue} · {event.city}
              </span>
              <span className="muted small">{dateTime(event.startsAt)}</span>
              {event.priceFrom && <span className="price">a partir de {money(event.priceFrom)}</span>}
            </button>
          ))}
          {events.length === 0 && !error && <p className="muted">Nenhum evento à venda.</p>}
        </section>
      )}
    </main>
  );
}
