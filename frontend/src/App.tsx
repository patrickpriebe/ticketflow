import { useEffect, useState } from 'react';
import {
  currentSession,
  getEvent,
  listEvents,
  placeOrder,
  ProblemError,
  signIn,
  signOut,
  type EventDetail,
  type EventSummary,
  type PaymentMethod,
  type Session,
} from './api';
import { Footer } from './components/Footer';
import { Header } from './components/Header';
import { Icon } from './components/Icon';
import {
  emptyCart,
  loadCart,
  saveCart,
  toOrderItems,
  type Cart,
} from './lib/cart';
import { navigate, useRoute } from './lib/router';
import { applyPreference, storedPreference } from './lib/theme';
import { useOrderStatus } from './useOrderStatus';
import { Checkout } from './views/Checkout';
import { Discover } from './views/Discover';
import { EventPage } from './views/EventPage';
import { Home } from './views/Home';
import { MyOrders } from './views/MyOrders';
import { OrderPage } from './views/OrderPage';
import { SignIn } from './views/SignIn';

export default function App() {
  const route = useRoute();

  const [session, setSession] = useState<Session | null>(currentSession);
  const [events, setEvents] = useState<EventSummary[]>([]);
  const [loadingEvents, setLoadingEvents] = useState(true);
  const [catalogError, setCatalogError] = useState<string | null>(null);

  const [event, setEvent] = useState<EventDetail | null>(null);
  const [cart, setCart] = useState<Cart | null>(loadCart);

  const [placing, setPlacing] = useState(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);

  /** Para onde voltar depois do login, para não perder o que estava sendo comprado. */
  const [returnTo, setReturnTo] = useState<string | null>(null);

  const { order, error: pollError } = useOrderStatus(route.name === 'order' ? route.id : null);

  // O tema é aplicado antes de qualquer coisa aparecer, senão a primeira pintura
  // sai clara e escurece um quadro depois.
  useEffect(() => {
    applyPreference(storedPreference());
  }, []);

  useEffect(() => {
    listEvents()
      .then((page) => setEvents(page.content))
      .catch(() => setCatalogError('Não foi possível carregar o catálogo. O Order Service está no ar?'))
      .finally(() => setLoadingEvents(false));
  }, []);

  // Detalhe do evento: só quando a rota é de um evento e o que está carregado não
  // é ele. Sem essa comparação, cada re-render dispararia uma requisição nova.
  useEffect(() => {
    if (route.name !== 'event' || event?.id === route.id) return;

    let cancelled = false;
    setEvent(null);
    getEvent(route.id)
      .then((detail) => {
        if (cancelled) return;
        setEvent(detail);
        // Carrinho é por evento: trocar de evento não carrega a escolha antiga junto.
        setCart((current) => (current?.eventId === detail.id ? current : emptyCart(detail)));
      })
      .catch(() => !cancelled && setCatalogError('Não foi possível carregar este evento.'));

    return () => {
      cancelled = true;
    };
  }, [route.name === 'event' ? route.id : null]);

  useEffect(() => {
    saveCart(cart);
  }, [cart]);

  const doSignIn = async (name: string, email: string) => {
    setSession(await signIn(name, email));
    navigate(returnTo ?? '/', { replace: true });
    setReturnTo(null);
  };

  const doSignOut = () => {
    signOut();
    setSession(null);
    navigate('/');
  };

  const confirmOrder = async (paymentMethod: PaymentMethod) => {
    if (!cart || cart.lines.length === 0) return;

    // Comprar exige sessão. Mandar para o login em vez de deixar a API responder
    // 401 e mostrar um erro seco — e guardar o caminho, para que entrar não custe
    // à pessoa refazer a escolha toda.
    if (!session) {
      setReturnTo('/checkout');
      navigate('/signin');
      return;
    }

    setPlacing(true);
    setCheckoutError(null);
    try {
      const created = await placeOrder({
        eventId: cart.eventId,
        items: toOrderItems(cart),
        paymentMethod,
      });
      setCart(null);
      navigate(`/orders/${created.id}`);
    } catch (e) {
      // Um 409 de estoque chega com o texto do backend, que já diz quantos restam.
      setCheckoutError(
        e instanceof ProblemError ? `${e.title}: ${e.detail}` : 'Não foi possível criar o pedido.',
      );
    } finally {
      setPlacing(false);
    }
  };

  return (
    <div className="app">
      <Header session={session} route={route} onSignOut={doSignOut} />

      <main>
        {catalogError && route.name !== 'order' && (
          <div className="shell" style={{ paddingTop: 'var(--space-5)' }}>
            <p className="alert">
              <Icon name="close" size={18} />
              {catalogError}
            </p>
          </div>
        )}

        {route.name === 'home' && <Home events={events} loading={loadingEvents} />}

        {route.name === 'events' && <Discover allEvents={events} query={route.query} />}

        {route.name === 'event' &&
          (event && cart ? (
            <EventPage event={event} cart={cart} onCart={setCart} />
          ) : (
            <LoadingEvent />
          ))}

        {route.name === 'checkout' &&
          (cart && cart.lines.length > 0 ? (
            <Checkout
              cart={cart}
              session={session}
              placing={placing}
              error={checkoutError}
              onConfirm={confirmOrder}
            />
          ) : (
            <EmptyCart />
          ))}

        {route.name === 'order' && <OrderPage order={order} error={pollError} />}

        {route.name === 'orders' &&
          (session ? (
            <MyOrders />
          ) : (
            <SignIn onSignIn={doSignIn} reason="Entre para ver os pedidos feitos com esta identidade." />
          ))}

        {route.name === 'signin' && (
          <SignIn
            onSignIn={doSignIn}
            reason={
              returnTo === '/checkout'
                ? 'Falta só identificar quem está comprando. Sua escolha de ingressos continua guardada.'
                : undefined
            }
          />
        )}

        {route.name === 'notFound' && <NotFound />}
      </main>

      <Footer />
    </div>
  );
}

function LoadingEvent() {
  return (
    <section className="shell section">
      <div className="skeleton" style={{ height: 320, borderRadius: 'var(--radius-lg)' }} />
    </section>
  );
}

function EmptyCart() {
  return (
    <section className="shell section narrow">
      <div className="empty">
        <Icon name="ticket" size={28} />
        <div>
          <strong>Seu carrinho está vazio.</strong>
          <p className="small">Escolha um evento e a quantidade de ingressos para continuar.</p>
        </div>
        <button className="btn btn-primary btn-sm" onClick={() => navigate('/events')}>
          Descobrir eventos
        </button>
      </div>
    </section>
  );
}

function NotFound() {
  return (
    <section className="shell section narrow">
      <div className="empty">
        <Icon name="inbox" size={28} />
        <div>
          <strong>Página não encontrada.</strong>
          <p className="small">O endereço não corresponde a nenhuma tela do TicketFlow.</p>
        </div>
        <button className="btn btn-primary btn-sm" onClick={() => navigate('/')}>
          Voltar para a home
        </button>
      </div>
    </section>
  );
}
