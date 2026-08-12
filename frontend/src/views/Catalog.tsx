import type { EventSummary } from '../api';
import { artwork } from '../lib/artwork';
import { calendarBadge, longDate, money, time } from '../lib/format';

interface Props {
  events: EventSummary[];
  loading: boolean;
  onOpen: (eventId: string) => void;
}

export function Catalog({ events, loading, onOpen }: Props) {
  return (
    <>
      <section className="hero">
        <div className="shell">
          <p className="eyebrow">Ingressos oficiais</p>
          <h1>
            Seu lugar nos melhores
            <br />
            <span className="gradient-text">shows, jogos e espetáculos</span>
          </h1>
          <p className="hero-sub">
            A compra é confirmada na hora e o pagamento roda em segundo plano — você
            acompanha o pedido mudando de status sem ficar preso numa tela de espera.
          </p>
        </div>
      </section>

      <section className="shell section">
        <div className="section-head">
          <h2>Em cartaz</h2>
          <span className="muted">{events.length} evento(s) à venda</span>
        </div>

        {loading ? (
          <div className="card-grid">
            {[0, 1, 2].map((i) => (
              <div key={i} className="event-card skeleton" aria-hidden="true">
                <div className="event-art" />
                <div className="event-body">
                  <div className="skeleton-line" />
                  <div className="skeleton-line short" />
                </div>
              </div>
            ))}
          </div>
        ) : events.length === 0 ? (
          <p className="empty">Nenhum evento à venda no momento.</p>
        ) : (
          <div className="card-grid">
            {events.map((event) => {
              const badge = calendarBadge(event.startsAt);
              return (
                <article key={event.id} className="event-card">
                  <div className="event-art" style={{ background: artwork(event.id).background }}>
                    <div className="date-badge">
                      <strong>{badge.day}</strong>
                      <span>{badge.month}</span>
                    </div>
                  </div>

                  <div className="event-body">
                    {/*
                      Botão esticado: o clique vale no card inteiro, mas quem
                      navega por teclado alcança um alvo real com nome acessível,
                      em vez de uma div que só responde ao mouse.
                    */}
                    <h3>
                      <button className="stretched" onClick={() => onOpen(event.id)}>
                        {event.name}
                      </button>
                    </h3>
                    <p className="muted small">
                      {longDate(event.startsAt)} · {time(event.startsAt)}
                    </p>
                    <p className="venue">
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" />
                        <circle cx="12" cy="10" r="3" />
                      </svg>
                      {event.venue} · {event.city}
                    </p>

                    <div className="event-foot">
                      {event.priceFrom && (
                        <span className="from">
                          a partir de <strong>{money(event.priceFrom)}</strong>
                        </span>
                      )}
                      <span className="cta">Ver ingressos →</span>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
    </>
  );
}
