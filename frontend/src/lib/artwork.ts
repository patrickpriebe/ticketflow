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

import type { Skin } from './skin';

export type Variant = 'rings' | 'bars' | 'blobs';

/*
 * As paletas saíram do mesmo lugar que o resto da interface: cortina, latão,
 * palco e a tinta escura da casa. Antes eram os azuis e roxos de painel de
 * produto — e um cartaz nessas cores dentro de um cartão de papel osso não
 * parecia do mesmo site, parecia de outro.
 *
 * Nenhuma delas passa por token: um SVG gerado não muda de cor com o tema, do
 * mesmo jeito que um cartaz impresso não muda de cor quando a luz da casa cai.
 */
const PALETTES: [string, string, string][] = [
  ['#8e1b25', '#c25a2a', '#e8b25a'], // cortina
  ['#7a4a08', '#b98b22', '#e8d3a0'], // latão
  ['#1f3d2e', '#4a7c59', '#c9dcae'], // palco
  ['#2b2233', '#6b3f6e', '#d9a3b0'], // camarim
  ['#11150f', '#7a5709', '#c9a227'], // marquise
  ['#5c1a12', '#a3421f', '#f0b48a'], // veludo
  ['#153042', '#38687e', '#bcd7dc'], // ribalta fria
  ['#3d2a10', '#8a6a2b', '#e3cf9a'], // madeira
];

/*
 * O desenho clássico tinha as suas: azuis, roxos e verdes de produto. Elas
 * continuam aqui porque o pôster é parte do desenho, e não um detalhe de cor —
 * um cartaz de bilheteria dentro do cartão azul e arredondado do desenho antigo
 * não parece do mesmo site.
 */
const CLASSIC_PALETTES: [string, string, string][] = [
  ['#0052ff', '#7b2ff7', '#00e0d0'],
  ['#bf3003', '#f59e0b', '#ffd166'],
  ['#0f766e', '#22d3ee', '#a7f3d0'],
  ['#6d28d9', '#db2777', '#fbcfe8'],
  ['#111827', '#0052ff', '#60a5fa'],
  ['#b91c1c', '#7c2d12', '#fca5a5'],
  ['#065f46', '#84cc16', '#fef08a'],
  ['#1e3a8a', '#0ea5e9', '#e0f2fe'],
];

/*
 * Cartaz é papel quente e quase branco. Os azuis e roxos do clássico brigam
 * com ele, e a paleta de cortina da bilheteria é escura demais para uma grade
 * sem borda: as duas fariam o cartaz parecer colado de outro lugar. Estas são
 * tintas de impressão em papel — terra, oliva, índigo apagado.
 */
const POSTER_PALETTES: [string, string, string][] = [
  ['#5c4a2e', '#a2560c', '#e3cba6'],
  ['#2f3d33', '#5c7a5e', '#cfd8c4'],
  ['#3a3550', '#6b6288', '#ccc6dd'],
  ['#5c2f2a', '#a4574a', '#e8c9be'],
  ['#2c3a4a', '#5b7794', '#c8d6e2'],
  ['#4a412c', '#8a7a4a', '#ddd3b4'],
  ['#42304a', '#7d5b86', '#dcc7de'],
  ['#2e4340', '#5e8a83', '#c6ddd8'],
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

export function artwork(id: string, skin: Skin = 'boxoffice'): Artwork {
  const seed = hash(id);
  // O cartaz gerado é parte do desenho, não um detalhe de cor: cada pele tem a
  // sua tinta. Palco divide a do clássico porque as duas partem do mesmo azul
  // de marca.
  const palettes =
    skin === 'boxoffice' ? PALETTES : skin === 'poster' ? POSTER_PALETTES : CLASSIC_PALETTES;
  const [from, to, accent] = palettes[seed % palettes.length];

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
