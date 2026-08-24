import { useMemo, useState } from 'react';
import type { EventSummary } from '../api';
import { CardSkeleton } from '../components/CardSkeleton';
import { EventCard } from '../components/EventCard';
import { Icon } from '../components/Icon';
import { Poster } from '../components/Poster';
import { calendarBadge, money, time } from '../lib/format';
import { linkProps, navigate } from '../lib/router';
import { usesFlatLayout, useSkin } from '../lib/skin';

interface Props {
  events: EventSummary[];
  loading: boolean;
}

/** Quantas sessões cabem no quadro sem ele virar uma segunda listagem. */
const BOARD_SIZE = 4;

interface SearchProps {
  term: string;
  onTerm: (value: string) => void;
  onSubmit: (event: React.FormEvent) => void;
}

/**
 * A home é a outra tela cuja estrutura muda com a pele — a abertura do
 * clássico é um banner centralizado com três números, a do de bilheteria é uma
 * marquise com o quadro de sessões ao lado. O miolo (destaques e programação) é
 * o mesmo nos dois, e só troca de roupa por CSS.
 */
export function Home({ events, loading }: Props) {
  const skin = useSkin();
  const [term, setTerm] = useState('');

  const [featured, ...rest] = events;
  const side = rest.slice(0, 2);
  const remaining = rest.slice(2);

  // O quadro é por data, não por destaque: é o que um quadro de sessões faz.
  // A ordem do catálogo não garante isso, então a cópia é ordenada aqui — sem
  // mexer no array que veio por prop, que os outros blocos ainda usam.
  const board = useMemo(
    () =>
      [...events]
        .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
        .slice(0, BOARD_SIZE),
    [events],
  );

  const search = (event: React.FormEvent) => {
    event.preventDefault();
    const query = term.trim();
    navigate(query ? `/events?q=${encodeURIComponent(query)}` : '/events');
  };

  const searchProps: SearchProps = { term, onTerm: setTerm, onSubmit: search };

  return (
    <>
      {usesFlatLayout(skin) ? (
        <ClassicHero {...searchProps} events={events} loading={loading} />
      ) : (
        <Marquee {...searchProps} board={board} loading={loading} />
      )}

      <section className="shell section">
        <div className="section-head">
          <div>
            <span className="label">Em cartaz</span>
            <h2>Mais procurados da semana</h2>
          </div>
          <a className="btn btn-secondary btn-sm" {...linkProps('/events')}>
            Ver todos
            <Icon name="chevron" size={13} />
          </a>
        </div>

        {loading ? (
          <div className="trending">
            <CardSkeleton variant="featured" />
            <div className="trending-side">
              <CardSkeleton variant="compact" />
              <CardSkeleton variant="compact" />
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

      {usesFlatLayout(skin) ? <ClassicPitch /> : <Terms />}

      {remaining.length > 0 && (
        <section className="shell section">
          <div className="section-head">
            <div>
              <span className="label">Programação</span>
              <h2>Próximos eventos</h2>
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

/* ------------------------------------------------------------------ *
 * Abertura: bilheteria
 * ------------------------------------------------------------------ */

/**
 * O topo não é um banner com uma frase no meio e três números embaixo. É a
 * marquise da casa: de um lado o que o lugar promete, do outro o quadro com as
 * próximas sessões — dado de verdade, vindo do catálogo, e cada linha é um
 * link. Um cartaz que mostra a programação vale mais do que um que fala sobre
 * si mesmo.
 */
function Marquee({
  term,
  onTerm,
  onSubmit,
  board,
  loading,
}: SearchProps & { board: EventSummary[]; loading: boolean }) {
  return (
    <section className="marquee">
      {/* A arte do painel é fixa: muda de composição só se a semente mudar, e
          uma home que troca de cara a cada visita cansa. */}
      <div className="marquee-art">
        <Poster seed="ticketflow-hero" priority />
      </div>

      <div className="shell marquee-grid">
        <div className="marquee-pitch">
          <span className="label">Bilheteria aberta</span>
          <h1>O ingresso é seu antes de a operadora responder</h1>
          <p>
            O lugar sai do estoque no instante em que você pede. A cobrança corre
            depois, em segundo plano, e você acompanha por aqui — sem ficar preso
            numa tela de espera.
          </p>

          <form className="marquee-search" onSubmit={onSubmit} role="search">
            <div className="search">
              <Icon name="search" size={18} />
              <input
                type="search"
                value={term}
                placeholder="Busque por evento, local ou cidade"
                aria-label="Buscar eventos"
                onChange={(e) => onTerm(e.target.value)}
              />
            </div>
            <button className="btn btn-primary" type="submit">
              Buscar
            </button>
          </form>
        </div>

        <aside className="board" aria-label="Próximas sessões">
          <div className="board-head">
            <span className="label">Próximas sessões</span>
            <span className="label">Por data</span>
          </div>

          {loading ? (
            <p className="board-empty">Carregando a programação…</p>
          ) : board.length === 0 ? (
            <p className="board-empty">Nenhuma sessão à venda no momento.</p>
          ) : (
            <ol>
              {board.map((event) => {
                const badge = calendarBadge(event.startsAt);
                return (
                  <li key={event.id} className="board-row">
                    <a {...linkProps(`/events/${event.id}`)}>
                      <span className="board-when">
                        {badge.day} {badge.month}
                      </span>
                      <span className="board-what">
                        {event.name}
                        <span className="board-where">
                          {event.venue} · {event.city} · {time(event.startsAt)}
                        </span>
                      </span>
                      <span className="board-price">
                        {event.priceFrom ? money(event.priceFrom) : 'Esgotado'}
                      </span>
                    </a>
                  </li>
                );
              })}
            </ol>
          )}

          <a className="board-foot" {...linkProps('/events')}>
            Ver a programação completa
          </a>
        </aside>
      </div>
    </section>
  );
}

/** O verso do ingresso: as condições que valem para toda compra feita aqui. */
function Terms() {
  return (
    <section className="shell">
      <div className="terms">
        <div className="terms-head">
          <span className="label">Vale para toda compra</span>
        </div>

        <div className="terms-grid">
          <div>
            <span className="label">01 · Reserva</span>
            <h3>O lugar sai do estoque no ato</h3>
            <p>
              O ingresso é reservado na mesma transação em que o pedido nasce. A
              cobrança vem depois e não segura você na tela.
            </p>
          </div>

          <div>
            <span className="label">02 · Cobrança</span>
            <h3>Uma cobrança por pedido</h3>
            <p>
              Cada pedido carrega uma chave própria. Se a rede cair no meio, reenviar
              devolve o pedido original em vez de cobrar de novo.
            </p>
          </div>

          <div>
            <span className="label">03 · Emissão</span>
            <h3>O ingresso sai no aceite</h3>
            <p>
              Aprovado o pagamento, o ingresso é emitido com código próprio e aparece
              aqui mesmo. Não é preciso esperar e-mail.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ *
 * Abertura: clássico
 *
 * A versão anterior do site, preservada inteira. Não é uma reconstrução por
 * aproximação — é a mesma marcação, para que quem escolher este desenho veja o
 * que existia antes e não uma imitação dele.
 * ------------------------------------------------------------------ */

function ClassicHero({
  term,
  onTerm,
  onSubmit,
  events,
  loading,
}: SearchProps & { events: EventSummary[]; loading: boolean }) {
  return (
    <section className="hero">
      <div className="hero-art">
        <Poster seed="ticketflow-hero" priority />
      </div>

      <div className="shell">
        <h1>Seu lugar nos melhores shows, jogos e espetáculos</h1>
        <p>
          Pedido aceito em milissegundos e pagamento resolvido em segundo plano.
          Você não fica preso numa tela de espera enquanto a operadora responde.
        </p>

        <form className="hero-search" onSubmit={onSubmit} role="search">
          <div className="search">
            <Icon name="search" size={18} />
            <input
              type="search"
              value={term}
              placeholder="Busque por evento, local ou cidade"
              aria-label="Buscar eventos"
              onChange={(e) => onTerm(e.target.value)}
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
  );
}

function ClassicPitch() {
  return (
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
  );
}
