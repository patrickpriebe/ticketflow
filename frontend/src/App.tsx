import { useCallback, useEffect, useState } from 'react';
import {
  currentSession,
  getEvent,
  listEvents,
  listMyOrders,
  placeOrder,
  ProblemError,
  signIn,
  signOut,
  type EventDetail,
  type EventSummary,
  type Order,
  type Session,
} from './api';
import { Header } from './components/Header';
import { money } from './lib/format';
import { useOrderStatus } from './useOrderStatus';
import { Catalog } from './views/Catalog';
import { EventPage } from './views/EventPage';
import { OrderPage } from './views/OrderPage';
import { SignIn } from './views/SignIn';

type View =
  | { name: 'catalog' }
  | { name: 'event'; event: EventDetail }
  | { name: 'order'; orderId: string }
  | { name: 'orders' }
  | { name: 'signin' };

export default function App() {
  const [session, setSession] = useState<Session | null>(currentSession);
  const [view, setView] = useState<View>({ name: 'catalog' });
  /** Para onde voltar depois do login, para não perder o que estava sendo comprado. */
  const [returnTo, setReturnTo] = useState<View | null>(null);
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [myOrders, setMyOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { order, error: pollError } = useOrderStatus(
    view.name === 'order' ? view.orderId : null,
  );

  useEffect(() => {
    listEvents()
      .then((page) => setEvents(page.content))
      .catch(() => setError('Não foi possível carregar os eventos.'))
      .finally(() => setLoading(false));
  }, []);

  const goHome = useCallback(() => {
    setView({ name: 'catalog' });
    setError(null);
  }, []);

  const openEvent = async (eventId: string) => {
    setError(null);
    try {
      setView({ name: 'event', event: await getEvent(eventId) });
    } catch {
      setError('Não foi possível carregar este evento.');
    }
  };

  const openMyOrders = async () => {
    setError(null);
    try {
      const page = await listMyOrders();
      setMyOrders(page.content);
      setView({ name: 'orders' });
    } catch (e) {
      setError(e instanceof ProblemError ? e.detail : 'Não foi possível carregar seus pedidos.');
    }
  };

  const buy = async (ticketCategoryId: string, quantity: number) => {
    if (view.name !== 'event') return;

    // Comprar exige sessão. Mandar para o login em vez de deixar a API responder
    // 401 e mostrar um erro seco - e guardar o evento, para que entrar não custe
    // ao cliente refazer o caminho todo.
    if (!session) {
      setReturnTo(view);
      setView({ name: 'signin' });
      return;
    }

    setPlacing(true);
    setError(null);
    try {
      const created = await placeOrder({ eventId: view.event.id, ticketCategoryId, quantity });
      setView({ name: 'order', orderId: created.id });
    } catch (e) {
      // Um 409 de estoque chega com o texto do backend, que já diz quantos restam.
      setError(e instanceof ProblemError ? `${e.title}: ${e.detail}` : 'Não foi possível criar o pedido.');
    } finally {
      setPlacing(false);
    }
  };

  const doSignIn = async (name: string, email: string) => {
    setSession(await signIn(name, email));
    // Volta para onde a pessoa estava, com o evento já aberto.
    setView(returnTo ?? { name: 'catalog' });
    setReturnTo(null);
  };

  const doSignOut = () => {
    signOut();
    setSession(null);
    goHome();
  };

  return (
    <div className="app">
      <Header session={session} onHome={goHome} onMyOrders={openMyOrders} onSignOut={doSignOut} />

      <main>
        {error && view.name !== 'order' && (
          <div className="shell">
            <p className="alert">{error}</p>
          </div>
        )}

        {view.name === 'catalog' && (
          <Catalog events={events} loading={loading} onOpen={openEvent} />
        )}

        {view.name === 'event' && (
          <EventPage event={view.event} placing={placing} onBack={goHome} onBuy={buy} />
        )}

        {view.name === 'order' && (
          <OrderPage order={order} error={pollError} onBack={goHome} />
        )}

        {view.name === 'signin' && <SignIn onSignIn={doSignIn} />}

        {view.name === 'orders' && (
          <section className="shell section narrow">
            <h2 className="section-title">Meus pedidos</h2>
            {myOrders.length === 0 ? (
              <p className="empty">Você ainda não fez nenhum pedido.</p>
            ) : (
              <ul className="order-list">
                {myOrders.map((o) => (
                  <li key={o.id}>
                    <button onClick={() => setView({ name: 'order', orderId: o.id })}>
                      <span>
                        <strong>{o.items.map((i) => `${i.quantity}× ${i.categoryName}`).join(', ')}</strong>
                        <span className="muted small"> · {money(o.totalAmount)}</span>
                      </span>
                      <span className={`pill pill-${o.status.toLowerCase()}`}>{o.status}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
      </main>

      <footer className="site-footer">
        <div className="shell">
          <span>TicketFlow · projeto de portfólio</span>
          <span className="muted small">
            Pedido aceito em milissegundos, pagamento resolvido de forma assíncrona
          </span>
        </div>
      </footer>
    </div>
  );
}
