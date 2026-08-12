/**
 * Arte do evento gerada a partir do id.
 *
 * O catálogo não tem imagem, e criar um campo `imageUrl` no contrato só para o
 * front ficar bonito seria o rabo abanando o cachorro — sem um lugar para
 * hospedar e um fluxo para subir arquivo, o campo nasceria vazio.
 *
 * Em vez do retângulo cinza de "sem imagem", cada evento ganha um pôster
 * desenhado a partir do próprio id: o mesmo evento tem sempre a mesma cara, a
 * grade fica visualmente variada e nada disso custa um único byte de rede.
 */

export type Variant = 'rings' | 'bars' | 'blobs';

const PALETTES: [string, string, string][] = [
  ['#0052ff', '#7b2ff7', '#00e0d0'],
  ['#bf3003', '#f59e0b', '#ffd166'],
  ['#0f766e', '#22d3ee', '#a7f3d0'],
  ['#6d28d9', '#db2777', '#fbcfe8'],
  ['#111827', '#0052ff', '#60a5fa'],
  ['#b91c1c', '#7c2d12', '#fca5a5'],
  ['#065f46', '#84cc16', '#fef08a'],
  ['#1e3a8a', '#0ea5e9', '#e0f2fe'],
];

const VARIANTS: Variant[] = ['rings', 'bars', 'blobs'];

/** Hash estável e barato. Não precisa ser criptográfico, precisa ser o mesmo sempre. */
function hash(value: string): number {
  let h = 2166136261;
  for (let i = 0; i < value.length; i++) {
    h ^= value.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return Math.abs(h);
}

export interface Artwork {
  from: string;
  to: string;
  accent: string;
  angle: number;
  variant: Variant;
  seed: number;
}

export function artwork(id: string): Artwork {
  const seed = hash(id);
  const [from, to, accent] = PALETTES[seed % PALETTES.length];

  return {
    from,
    to,
    accent,
    angle: 110 + (seed % 6) * 20,
    variant: VARIANTS[(seed >> 3) % VARIANTS.length],
    seed,
  };
}

/**
 * Gerador pseudoaleatório determinístico: dado o mesmo id, a mesma sequência.
 * `Math.random()` aqui faria o pôster mudar a cada renderização.
 */
export function sequence(seed: number, count: number): number[] {
  const values: number[] = [];
  let state = seed || 1;
  for (let i = 0; i < count; i++) {
    state = (state * 1664525 + 1013904223) % 4294967296;
    values.push(state / 4294967296);
  }
  return values;
}
