import { useState } from 'react';
import type { EventSummary } from '../api';
import { EventCard } from '../components/EventCard';
import { Icon } from '../components/Icon';
import { Poster } from '../components/Poster';
import { linkProps, navigate } from '../lib/router';

interface Props {
  events: EventSummary[];
  loading: boolean;
}

export function Home({ events, loading }: Props) {
  const [term, setTerm] = useState('');

  const [featured, ...rest] = events;
  const side = rest.slice(0, 2);
  const remaining = rest.slice(2);

  const search = (event: React.FormEvent) => {
    event.preventDefault();
    const query = term.trim();
    navigate(query ? `/events?q=${encodeURIComponent(query)}` : '/events');
  };

  return (
    <>
      <section className="hero">
        {/* A arte do hero é fixa: muda de composição só se a semente mudar, e
            uma home que troca de cara a cada visita cansa. */}
        <div className="hero-art">
          <Poster seed="ticketflow-hero" />
        </div>

        <div className="shell">
          <h1>Seu lugar nos melhores shows, jogos e espetáculos</h1>
          <p>
            Pedido aceito em milissegundos e pagamento resolvido em segundo plano.
            Você não fica preso numa tela de espera enquanto a operadora responde.
          </p>

          <form className="hero-search" onSubmit={search} role="search">
            <div className="search">
              <Icon name="search" size={18} />
              <input
                type="search"
                value={term}
                placeholder="Busque por evento, local ou cidade"
                aria-label="Buscar eventos"
                onChange={(e) => setTerm(e.target.value)}
              />
            </div>
            <button className="btn btn-primary" type="submit">
              Buscar
            </button>
          </form>

          <div className="hero-stats">
            <div>
              <strong>{loading ? '—' : events.length}</strong>
              eventos à venda
            </div>
            <div>
              <strong>3</strong>
              formas de pagamento
            </div>
            <div>
              <strong>~200ms</strong>
              para o pedido ser aceito
            </div>
          </div>
        </div>
      </section>

      <section className="shell section">
        <div className="section-head">
          <div>
            <h2>Em alta</h2>
            <p>Os eventos mais procurados desta semana.</p>
          </div>
          <a className="btn btn-secondary btn-sm" {...linkProps('/events')}>
            Ver todos
            <Icon name="chevron" size={14} />
          </a>
        </div>

        {loading ? (
          <div className="trending">
            <div className="event-card">
              <div className="event-art skeleton" />
              <div className="event-card-body stack">
                <div className="skeleton skeleton-line" />
                <div className="skeleton skeleton-line short" />
              </div>
            </div>
            <div className="trending-side">
              {[0, 1].map((i) => (
                <div key={i} className="event-card">
                  <div className="event-art skeleton" />
                  <div className="event-card-body stack">
                    <div className="skeleton skeleton-line" />
                    <div className="skeleton skeleton-line short" />
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : featured ? (
          <div className="trending">
            <EventCard event={featured} variant="featured" />
            <div className="trending-side">
              {side.map((event) => (
                <EventCard key={event.id} event={event} variant="compact" />
              ))}
            </div>
          </div>
        ) : (
          <p className="empty">
            <Icon name="inbox" size={28} />
            Nenhum evento à venda no momento.
          </p>
        )}
      </section>

      <section className="shell">
        <div className="pitch">
          <div className="pitch-item">
            <span className="icon-badge">
              <Icon name="bolt" size={18} />
            </span>
            <h3>Confirmação imediata</h3>
            <p>
              O ingresso é reservado na mesma transação em que o pedido nasce. A
              cobrança acontece depois, sem segurar você na tela.
            </p>
          </div>

          <div className="pitch-item">
            <span className="icon-badge">
              <Icon name="shield" size={18} />
            </span>
            <h3>Cobrança uma vez só</h3>
            <p>
              Cada pedido carrega uma chave de idempotência. Se a rede cair no meio,
              repetir o envio devolve o pedido original em vez de cobrar de novo.
            </p>
          </div>

          <div className="pitch-item">
            <span className="icon-badge">
              <Icon name="ticket" size={18} />
            </span>
            <h3>Ingresso na hora do aceite</h3>
            <p>
              Aprovado o pagamento, o ingresso é emitido com código próprio e fica
              disponível aqui mesmo, sem esperar e-mail.
            </p>
          </div>
        </div>
      </section>

      {remaining.length > 0 && (
        <section className="shell section">
          <div className="section-head">
            <div>
              <h2>Próximos eventos</h2>
              <p>Programação completa, por data.</p>
            </div>
          </div>
          <div className="card-grid">
            {remaining.map((event) => (
              <EventCard key={event.id} event={event} />
            ))}
          </div>
        </section>
      )}
    </>
  );
}
