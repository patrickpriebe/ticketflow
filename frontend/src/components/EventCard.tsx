import type { EventSummary } from '../api';
import { calendarBadge, daysUntil, longDate, money, time } from '../lib/format';
import { linkProps } from '../lib/router';
import { Icon } from './Icon';
import { Poster } from './Poster';

interface Props {
  event: EventSummary;
  /** `featured` é o cartão grande da home; `compact` é o de lista lateral. */
  variant?: 'default' | 'featured' | 'compact';
}

export function EventCard({ event, variant = 'default' }: Props) {
  const badge = calendarBadge(event.startsAt);
  const days = daysUntil(event.startsAt);

  // `priceFrom` é o menor preço entre as categorias que ainda têm ingresso. Vir
  // vazio significa que todas esgotaram — o backend não tem um campo "esgotado",
  // e ele não faria falta: a ausência de preço já diz isso.
  const soldOut = !event.priceFrom;

  return (
    <article className={`event-card${variant === 'featured' ? ' featured' : ''}`}>
      <div className="event-art">
        <Poster seed={event.id} />
        {soldOut ? (
          <span className="art-badge">Esgotado</span>
        ) : days <= 21 ? (
          <span className="art-badge urgent">
            <Icon name="bolt" size={12} />
            Últimos dias
          </span>
        ) : null}

        <div className="date-badge">
          <strong>{badge.day}</strong>
          <span>{badge.month}</span>
        </div>
      </div>

      <div className="event-card-body">
        <h3>
          <a className="stretched" {...linkProps(`/events/${event.id}`)}>
            {event.name}
          </a>
        </h3>

        <p className="event-meta">
          <Icon name="calendar" size={14} />
          {longDate(event.startsAt)} · {time(event.startsAt)}
        </p>

        <p className="event-meta">
          <Icon name="pin" size={14} />
          {event.venue} · {event.city}
        </p>

        <div className="event-card-foot">
          {event.priceFrom ? (
            <span className="price-from">
              a partir de <strong>{money(event.priceFrom)}</strong>
            </span>
          ) : (
            <span className="price-from">
              <strong>Esgotado</strong>
            </span>
          )}

          {/* O cartão estreito da lateral não tem largura para o preço e a
              chamada lado a lado sem um deles quebrar em duas linhas. */}
          {variant !== 'compact' && (
            <span className="row small" style={{ color: 'var(--brand-500)', fontWeight: 600 }}>
              Ver ingressos
              <Icon name="chevron" size={14} />
            </span>
          )}
        </div>
      </div>
    </article>
  );
}
