import { useState } from 'react';
import type { EventDetail, PaymentMethod } from '../api';
import { artwork } from '../lib/artwork';
import { longDate, money, time } from '../lib/format';

interface Props {
  event: EventDetail;
  placing: boolean;
  onBack: () => void;
  onBuy: (ticketCategoryId: string, quantity: number, paymentMethod: PaymentMethod) => void;
}

const MAX_PER_ORDER = 10;

/**
 * Os três métodos que o backend aceita — e cada um segue uma `PaymentStrategy`
 * diferente lá dentro: endpoint próprio no gateway e política de retry distinta.
 * Antes o front mandava CREDIT_CARD fixo, então essa parte do sistema nunca era
 * exercitada por quem usava a tela.
 */
const METHODS: { id: PaymentMethod; label: string; hint: string }[] = [
  { id: 'CREDIT_CARD', label: 'Cartão de crédito', hint: 'Aprovação na hora' },
  { id: 'PIX', label: 'PIX', hint: 'Confirmação em segundos' },
  { id: 'BOLETO', label: 'Boleto', hint: 'Compensa em até 3 dias' },
];

export function EventPage({ event, placing, onBack, onBuy }: Props) {
  const [selected, setSelected] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [method, setMethod] = useState<PaymentMethod>('CREDIT_CARD');

  const category = event.categories.find((c) => c.id === selected) ?? null;
  // O teto é o menor entre o limite por pedido e o que ainda existe em estoque —
  // o backend recusaria de qualquer jeito, mas deixar o botão clicável para depois
  // mostrar erro é desrespeito com quem está comprando.
  const max = category ? Math.min(MAX_PER_ORDER, category.availableQuantity) : MAX_PER_ORDER;
  const total = category ? category.price.amount * quantity : 0;

  const select = (id: string) => {
    setSelected(id);
    setQuantity(1);
  };

  return (
    <>
      <div className="event-hero" style={{ background: artwork(event.id).background }}>
        <div className="shell">
          <button className="back" onClick={onBack}>
            ← Voltar
          </button>
          <h1>{event.name}</h1>
          <p className="event-hero-meta">
            {longDate(event.startsAt)} · {time(event.startsAt)} · {event.venue}, {event.city}
          </p>
        </div>
      </div>

      <section className="shell section two-col">
        <div>
          <h2 className="section-title">Escolha seu ingresso</h2>

          <ul className="ticket-list">
            {event.categories.map((c) => {
              const soldOut = c.availableQuantity === 0;
              const active = c.id === selected;
              return (
                <li key={c.id}>
                  <button
                    className={`ticket-option${active ? ' active' : ''}${soldOut ? ' sold-out' : ''}`}
                    disabled={soldOut}
                    onClick={() => select(c.id)}
                  >
                    <span className="radio" aria-hidden="true" />
                    <span className="ticket-info">
                      <strong>{c.name}</strong>
                      <span className="muted small">
                        {soldOut
                          ? 'Esgotado'
                          : c.availableQuantity < 50
                            ? `Últimos ${c.availableQuantity} ingressos`
                            : `${c.availableQuantity} disponíveis`}
                      </span>
                    </span>
                    <span className="ticket-price">{money(c.price)}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        </div>

        <aside className="summary-card">
          <h3>Resumo</h3>

          {!category ? (
            <p className="muted small">Selecione um tipo de ingresso ao lado.</p>
          ) : (
            <>
              <div className="summary-row">
                <span>{category.name}</span>
                <span>{money(category.price)}</span>
              </div>

              <div className="stepper">
                <span className="muted small">Quantidade</span>
                <div className="stepper-controls">
                  <button onClick={() => setQuantity((q) => Math.max(1, q - 1))} disabled={quantity <= 1}>
                    −
                  </button>
                  <strong>{quantity}</strong>
                  <button onClick={() => setQuantity((q) => Math.min(max, q + 1))} disabled={quantity >= max}>
                    +
                  </button>
                </div>
              </div>

              <div className="method-picker">
                <span className="muted small">Forma de pagamento</span>
                <div className="methods">
                  {METHODS.map((m) => (
                    <button
                      key={m.id}
                      className={`method${m.id === method ? ' active' : ''}`}
                      onClick={() => setMethod(m.id)}
                    >
                      <strong>{m.label}</strong>
                      <span className="muted small">{m.hint}</span>
                    </button>
                  ))}
                </div>
              </div>

              <div className="summary-total">
                <span>Total</span>
                <strong>{money({ amount: total, currency: category.price.currency })}</strong>
              </div>

              <button
                className="primary block"
                disabled={placing}
                onClick={() => onBuy(category.id, quantity, method)}
              >
                {placing ? 'Enviando…' : 'Comprar agora'}
              </button>

              <p className="fine-print">
                O pedido é aceito na hora. O pagamento é processado logo em seguida e
                você acompanha o status na próxima tela.
              </p>
            </>
          )}
        </aside>
      </section>
    </>
  );
}
