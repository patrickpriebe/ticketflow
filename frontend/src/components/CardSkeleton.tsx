import { usesFlatLayout, useSkin } from '../lib/skin';

interface Props {
  variant?: 'default' | 'featured' | 'compact';
}

/**
 * A caixa cinza que ocupa o lugar de um cartão de evento enquanto o catálogo
 * não chega.
 *
 * Acompanha a pele porque os dois desenhos têm caixas diferentes: o clássico é
 * um retângulo só, o de bilheteria tem canhoto. Um esqueleto com a forma errada
 * faz a página pular no instante em que os dados chegam, que é exatamente o que
 * um esqueleto existe para evitar.
 */
export function CardSkeleton({ variant = 'default' }: Props) {
  const skin = useSkin();

  if (usesFlatLayout(skin)) {
    return (
      <div className={`event-card${variant === 'featured' ? ' featured' : ''}`}>
        <div className="event-art skeleton" />
        <div className="event-card-body stack">
          <div className="skeleton skeleton-line" />
          <div className="skeleton skeleton-line short" />
        </div>
      </div>
    );
  }

  return (
    <div className={`event-ticket ${variant}`}>
      <div className="event-ticket-main">
        <div className="event-art skeleton" />
        <div className="event-body stack">
          <div className="skeleton skeleton-line" />
          <div className="skeleton skeleton-line short" />
        </div>
      </div>
      <div className="event-stub" />
    </div>
  );
}
