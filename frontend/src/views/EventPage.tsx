import type { EventDetail } from '../api';
import { Icon } from '../components/Icon';
import { Poster } from '../components/Poster';
import { photoFor } from '../lib/eventPhotos';
import {
  canAddCategory,
  cartCount,
  cartTotal,
  MAX_CATEGORIES,
  quantityOf,
  setQuantity,
  type Cart,
} from '../lib/cart';
import { longDate, money, time } from '../lib/format';
import { navigate } from '../lib/router';

interface Props {
  event: EventDetail;
  cart: Cart;
  onCart: (cart: Cart) => void;
}

/** Teto por categoria, igual ao que o contrato aceita em `quantity`. */
const MAX_PER_CATEGORY = 10;

export function EventPage({ event, cart, onCart }: Props) {
  const count = cartCount(cart);
  const total = cartTotal(cart);
  const chosen = cart.lines;
  const photo = photoFor(event.id);

  return (
    <>
      <div className="event-hero">
        <div className="hero-art">
          <Poster seed={event.id} alt={`${event.venue}, ${event.city}`} priority />
        </div>

        {photo && (
          // Crédito onde a foto aparece grande. As licenças CC BY e CC BY-SA
          // exigem atribuição, e a lista completa fica em
          // frontend/public/img/events/CREDITS.json.
          <p className="photo-credit">
            Foto:{' '}
            <a href={photo.source} target="_blank" rel="noreferrer noopener">
              {photo.author}
            </a>{' '}
            ({photo.license})
          </p>
        )}

        <div className="shell">
          <button className="back" onClick={() => navigate('/events')}>
            <Icon name="arrow-left" size={16} />
            Voltar para o catálogo
          </button>

          <div className="event-tags">
            <span className="pill pill-brand">À venda</span>
            <span className="pill pill-on-art">{event.city}</span>
          </div>

          <h1>{event.name}</h1>

          <p className="event-hero-meta">
            <span>
              <Icon name="calendar" size={15} />
              {longDate(event.startsAt)}
            </span>
            <span>
              <Icon name="clock" size={15} />
              {time(event.startsAt)}
            </span>
            <span>
              <Icon name="pin" size={15} />
              {event.venue}, {event.city}
            </span>
          </p>
        </div>
      </div>

      <section className="shell section two-col">
        <div>
          {event.description && (
            <div className="block">
              <h2>Sobre o evento</h2>
              <div className="prose" style={{ marginTop: 'var(--space-4)' }}>
                <p>{event.description}</p>
              </div>
            </div>
          )}

          <div className="block">
            <h2>Local</h2>
            <div className="venue-card" style={{ marginTop: 'var(--space-4)' }}>
              {/* Sem mapa de verdade: o catálogo não guarda coordenadas, e um
                  iframe de mapa só para preencher o espaço traria um terceiro
                  rastreando quem abre a página. */}
              <div className="venue-map">
                <Icon name="pin" size={24} />
              </div>
              <div>
                <h3>{event.venue}</h3>
                <p className="muted small">{event.city}</p>
                <ul className="venue-facts">
                  <li>
                    <Icon name="clock" size={14} />
                    Abertura dos portões duas horas antes.
                  </li>
                  <li>
                    <Icon name="ticket" size={14} />
                    Ingresso digital com código próprio, apresentado na portaria.
                  </li>
                  <li>
                    <Icon name="shield" size={14} />
                    Meia-entrada mediante comprovação na entrada.
                  </li>
                </ul>
              </div>
            </div>
          </div>

          <div className="block">
            <h2>Vendas</h2>
            <div className="note" style={{ marginTop: 'var(--space-4)' }}>
              <Icon name="bolt" size={18} />
              <div>
                O pedido é aceito na hora e os ingressos ficam reservados enquanto o
                pagamento é processado. Se a cobrança não for aprovada dentro do prazo,
                a reserva é desfeita e os ingressos voltam para o estoque
                automaticamente.
              </div>
            </div>
          </div>
        </div>

        <aside className="buy-panel">
          <div className="buy-panel-head">
            <h3>Escolha seus ingressos</h3>
            {count > 0 && <span className="pill pill-brand">{count} no carrinho</span>}
          </div>

          <ul className="tier-list">
            {event.categories.map((category) => {
              const quantity = quantityOf(cart, category.id);
              const soldOut = category.availableQuantity === 0;
              const max = Math.min(MAX_PER_CATEGORY, category.availableQuantity);
              // O pedido aceita no máximo dez categorias. Barrar aqui evita
              // escolher a décima primeira e só descobrir no envio.
              const blockedByCartLimit = !canAddCategory(cart, category.id);

              const change = (next: number) =>
                onCart(
                  setQuantity(
                    cart,
                    {
                      categoryId: category.id,
                      name: category.name,
                      unitPrice: category.price,
                      available: category.availableQuantity,
                    },
                    next,
                  ),
                );

              return (
                <li
                  key={category.id}
                  className={`tier${quantity > 0 ? ' chosen' : ''}${soldOut ? ' sold-out' : ''}`}
                  style={{ flexDirection: 'column', alignItems: 'stretch' }}
                >
                  <div className="row" style={{ gap: 'var(--space-3)' }}>
                    <div className="tier-info">
                      <strong>{category.name}</strong>
                      <span className="muted small">
                        {soldOut
                          ? 'Esgotado'
                          : category.availableQuantity < 50
                            ? `Últimos ${category.availableQuantity} ingressos`
                            : `${category.availableQuantity} disponíveis`}
                      </span>
                    </div>
                    <span className="tier-price">{money(category.price)}</span>
                  </div>

                  {!soldOut && (
                    <div className="tier-foot">
                      <span className="muted small">
                        {quantity > 0
                          ? money({ amount: category.price.amount * quantity, currency: category.price.currency })
                          : 'Quantidade'}
                      </span>
                      <div className="qty">
                        <button
                          aria-label={`Remover um ${category.name}`}
                          disabled={quantity === 0}
                          onClick={() => change(quantity - 1)}
                        >
                          −
                        </button>
                        <strong aria-live="polite">{quantity}</strong>
                        <button
                          aria-label={`Adicionar um ${category.name}`}
                          disabled={quantity >= max || blockedByCartLimit}
                          title={
                            blockedByCartLimit
                              ? `Um pedido leva no máximo ${MAX_CATEGORIES} tipos de ingresso.`
                              : undefined
                          }
                          onClick={() => change(quantity + 1)}
                        >
                          +
                        </button>
                      </div>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>

          {chosen.length > 0 && (
            <>
              <div className="stack" style={{ gap: 'var(--space-2)' }}>
                {chosen.map((line) => (
                  <div key={line.categoryId} className="summary-line">
                    <span>
                      {line.quantity}× {line.name}
                    </span>
                    <span>
                      {money({ amount: line.unitPrice.amount * line.quantity, currency: line.unitPrice.currency })}
                    </span>
                  </div>
                ))}
              </div>

              <div className="summary-total">
                <span>Total</span>
                <strong>{money(total)}</strong>
              </div>
            </>
          )}

          <button
            className="btn btn-primary btn-lg btn-block"
            disabled={count === 0}
            onClick={() => navigate('/checkout')}
          >
            {count === 0 ? 'Selecione um ingresso' : 'Ir para o pagamento'}
          </button>

          <p className="fine-print">
            Nada é cobrado nesta etapa. O pagamento é escolhido no próximo passo.
          </p>
        </aside>
      </section>
    </>
  );
}
