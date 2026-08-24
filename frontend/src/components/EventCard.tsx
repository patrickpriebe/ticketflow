import type { EventSummary } from '../api';
import { calendarBadge, daysUntil, longDate, money, time, weekdayShort } from '../lib/format';
import { linkProps } from '../lib/router';
import { usesFlatLayout, useSkin } from '../lib/skin';
import { Icon } from './Icon';
import { Poster } from './Poster';

interface Props {
  event: EventSummary;
  /** `featured` é o cartão grande da home; `compact` é o de lista lateral. */
  variant?: 'default' | 'featured' | 'compact';
}

/**
 * O cartão de evento, nos dois desenhos.
 *
 * Este é um dos dois únicos lugares do projeto onde a pele muda a *estrutura* e
 * não só a cor — por isso o ramo aqui em vez de mais uma regra de CSS. No
 * desenho clássico o cartão é um retângulo com selo de calendário sobre a arte;
 * no de bilheteria é um ingresso, com canhoto destacável e picotagem.
 */
export function EventCard({ event, variant = 'default' }: Props) {
  const skin = useSkin();
  const badge = calendarBadge(event.startsAt);
  const days = daysUntil(event.startsAt);

  // `priceFrom` é o menor preço entre as categorias que ainda têm ingresso. Vir
  // vazio significa que todas esgotaram — o backend não tem um campo "esgotado",
  // e ele não faria falta: a ausência de preço já diz isso.
  const soldOut = !event.priceFrom;

  if (usesFlatLayout(skin)) {
    return (
      <article className={`event-card${variant === 'featured' ? ' featured' : ''}`}>
        <div className="event-art">
          <Poster seed={event.id} priority={variant === 'featured'} />
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

  /*
   * Bilheteria: corpo à esquerda, canhoto destacável à direita, picotagem entre
   * os dois com entalhe em cima e embaixo.
   *
   * O canhoto carrega a data e nada além dela: dia, mês e, no trilho vertical,
   * o dia da semana. Ele é `aria-hidden` porque a data completa já está escrita
   * por extenso no corpo do cartão — anunciar as duas faz o leitor de tela ler
   * a mesma data duas vezes.
   */
  return (
    <article className={`event-ticket ${variant}`}>
      <div className="event-ticket-main">
        <div className="event-art">
          <Poster seed={event.id} priority={variant === 'featured'} />
          {soldOut ? (
            <span className="art-flag">Esgotado</span>
          ) : days <= 21 ? (
            <span className="art-flag urgent">
              <Icon name="bolt" size={11} />
              Últimos dias
            </span>
          ) : null}
        </div>

        <div className="event-body">
          <h3>
            <a className="stretched" {...linkProps(`/events/${event.id}`)}>
              {event.name}
            </a>
          </h3>

          <p className="event-meta">
            <Icon name="clock" size={13} />
            <span>
              {longDate(event.startsAt)} · <span className="num">{time(event.startsAt)}</span>
            </span>
          </p>

          <p className="event-meta">
            <Icon name="pin" size={13} />
            <span>
              {event.venue} · {event.city}
            </span>
          </p>

          <div className="event-foot">
            {event.priceFrom ? (
              <span className="price-from">
                <span className="label">a partir de</span>
                <strong className="num">{money(event.priceFrom)}</strong>
              </span>
            ) : (
              <span className="price-from">
                <span className="label">bilheteria</span>
                <strong>Esgotado</strong>
              </span>
            )}

            {variant !== 'compact' && (
              <span className="event-go">
                Ver ingressos
                <Icon name="chevron" size={13} />
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="event-stub" aria-hidden="true">
        <span className="stub-day num">{badge.day}</span>
        <span className="stub-month">{badge.month}</span>
        <span className="stub-rail">{weekdayShort(event.startsAt)}</span>
      </div>
    </article>
  );
}
