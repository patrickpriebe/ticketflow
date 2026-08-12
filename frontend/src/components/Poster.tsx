import { useId } from 'react';
import { artwork, sequence } from '../lib/artwork';

interface Props {
  /** Semente do desenho. O mesmo id devolve sempre o mesmo pôster. */
  seed: string;
  className?: string;
}

/**
 * O pôster do evento, desenhado em SVG a partir do id.
 *
 * Três composições — anéis, barras e manchas — sobre um degradê de duas cores.
 * Não é aleatório: a sequência vem de um gerador determinístico, senão a arte
 * mudaria a cada re-render e a grade piscaria a cada digitação na busca.
 */
export function Poster({ seed, className }: Props) {
  const art = artwork(seed);
  // Um id por instância: dois gradientes com o mesmo id no documento e o
  // segundo elemento passa a usar as cores do primeiro.
  const gradientId = useId();

  const random = sequence(art.seed, 24);

  return (
    <svg
      className={className}
      viewBox="0 0 400 250"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
      style={{ width: '100%', height: '100%' }}
    >
      <defs>
        <linearGradient id={gradientId} gradientTransform={`rotate(${art.angle - 90} 0.5 0.5)`}>
          <stop offset="0%" stopColor={art.from} />
          <stop offset="100%" stopColor={art.to} />
        </linearGradient>
      </defs>

      <rect width="400" height="250" fill={`url(#${gradientId})`} />

      {art.variant === 'rings' &&
        random.slice(0, 6).map((value, i) => (
          <circle
            key={i}
            cx={60 + value * 300}
            cy={40 + random[i + 6] * 180}
            r={30 + random[i + 12] * 90}
            fill="none"
            stroke={art.accent}
            strokeWidth={1 + random[i] * 3}
            opacity={0.25 + random[i + 6] * 0.35}
          />
        ))}

      {art.variant === 'bars' &&
        random.slice(0, 18).map((value, i) => {
          const height = 30 + value * 190;
          return (
            <rect
              key={i}
              x={i * 23 + 6}
              y={250 - height}
              width="12"
              height={height}
              rx="6"
              fill={art.accent}
              opacity={0.2 + random[i] * 0.4}
            />
          );
        })}

      {art.variant === 'blobs' &&
        random.slice(0, 5).map((value, i) => (
          <ellipse
            key={i}
            cx={value * 420 - 10}
            cy={random[i + 5] * 260 - 5}
            rx={70 + random[i + 10] * 110}
            ry={50 + random[i + 15] * 80}
            fill={i % 2 === 0 ? art.accent : '#ffffff'}
            opacity={0.14 + random[i] * 0.18}
          />
        ))}

      {/* Vinheta: segura o texto branco que fica por cima nos heros. */}
      <rect width="400" height="250" fill="#000" opacity="0.12" />
    </svg>
  );
}
