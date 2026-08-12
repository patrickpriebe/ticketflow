import { useEffect, useState } from 'react';
import { listEvents, type EventSummary } from '../api';
import { EventCard } from '../components/EventCard';
import { Icon } from '../components/Icon';
import { navigate } from '../lib/router';

interface Props {
  /** Catálogo completo, carregado uma vez pelo App. Serve para montar a lista de cidades. */
  allEvents: EventSummary[];
  query: URLSearchParams;
}

export function Discover({ allEvents, query }: Props) {
  const city = query.get('city') ?? '';
  const term = query.get('q') ?? '';
  const min = query.get('min') ?? '';
  const max = query.get('max') ?? '';

  const [results, setResults] = useState<EventSummary[]>(allEvents);
  const [loading, setLoading] = useState(false);

  /*
   * Cidade é filtro do backend — o parâmetro existe no catálogo e é ele que
   * decide o que vem. Preço e texto são filtrados aqui, sobre a página já
   * carregada, porque o endpoint não tem esses parâmetros. Com o catálogo
   * atual cabe tudo numa página; quando não couber, filtrar no cliente passa a
   * mentir, e a resposta certa é levar os dois para o backend.
   */
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    listEvents(city ? { city } : {})
      .then((page) => !cancelled && setResults(page.content))
      .catch(() => !cancelled && setResults([]))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [city]);

  const cities = [...new Set(allEvents.map((event) => event.city))].sort();

  const filtered = results.filter((event) => {
    const haystack = `${event.name} ${event.venue} ${event.city}`.toLowerCase();
    if (term && !haystack.includes(term.toLowerCase())) return false;

    const price = event.priceFrom?.amount;
    if (min && (price === undefined || price < Number(min))) return false;
    if (max && (price === undefined || price > Number(max))) return false;
    return true;
  });

  const update = (key: string, value: string) => {
    const next = new URLSearchParams(query);
    if (value) next.set(key, value);
    else next.delete(key);
    const search = next.toString();
    navigate(search ? `/events?${search}` : '/events');
  };

  const active = [
    term && { key: 'q', label: `"${term}"` },
    city && { key: 'city', label: city },
    min && { key: 'min', label: `a partir de R$ ${min}` },
    max && { key: 'max', label: `até R$ ${max}` },
  ].filter(Boolean) as { key: string; label: string }[];

  return (
    <section className="shell section discover">
      <aside className="filters">
        <div className="filter-group">
          <h4>Cidade</h4>
          <div className="chip-row">
            <button className="chip" aria-pressed={!city} onClick={() => update('city', '')}>
              Todas
            </button>
            {cities.map((option) => (
              <button
                key={option}
                className="chip"
                aria-pressed={city === option}
                onClick={() => update('city', option)}
              >
                {option}
              </button>
            ))}
          </div>
        </div>

        <div className="filter-group">
          <h4>Preço a partir de</h4>
          <div className="range">
            <input
              className="input"
              type="number"
              min="0"
              inputMode="numeric"
              placeholder="mín."
              aria-label="Preço mínimo"
              value={min}
              onChange={(e) => update('min', e.target.value)}
            />
            <span className="muted">–</span>
            <input
              className="input"
              type="number"
              min="0"
              inputMode="numeric"
              placeholder="máx."
              aria-label="Preço máximo"
              value={max}
              onChange={(e) => update('max', e.target.value)}
            />
          </div>
        </div>

        <div className="filter-group">
          <h4>Busca</h4>
          <div className="search">
            <Icon name="search" />
            <input
              type="search"
              value={term}
              placeholder="Nome, local ou cidade"
              aria-label="Buscar no catálogo"
              onChange={(e) => update('q', e.target.value)}
            />
          </div>
        </div>
      </aside>

      <div>
        <div className="section-head">
          <div>
            <h1>Descobrir</h1>
            <p>
              {loading
                ? 'Carregando…'
                : `${filtered.length} ${filtered.length === 1 ? 'evento encontrado' : 'eventos encontrados'}`}
            </p>
          </div>
        </div>

        {active.length > 0 && (
          <div className="active-filters">
            Filtros ativos:
            {active.map((filter) => (
              <button key={filter.key} className="filter-tag" onClick={() => update(filter.key, '')}>
                {filter.label}
                <Icon name="close" size={12} />
              </button>
            ))}
            <button className="btn btn-ghost btn-sm" onClick={() => navigate('/events')}>
              Limpar tudo
            </button>
          </div>
        )}

        {loading ? (
          <div className="card-grid">
            {[0, 1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="event-card">
                <div className="event-art skeleton" />
                <div className="event-card-body stack">
                  <div className="skeleton skeleton-line" />
                  <div className="skeleton skeleton-line short" />
                </div>
              </div>
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="empty">
            <Icon name="inbox" size={28} />
            <div>
              <strong>Nenhum evento com esses filtros.</strong>
              <p className="small">Tente ampliar a faixa de preço ou remover a cidade.</p>
            </div>
            <button className="btn btn-secondary btn-sm" onClick={() => navigate('/events')}>
              Limpar filtros
            </button>
          </div>
        ) : (
          <div className="card-grid">
            {filtered.map((event) => (
              <EventCard key={event.id} event={event} />
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
