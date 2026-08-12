/**
 * Arte do evento gerada a partir do id.
 *
 * O catálogo não tem imagem — e não vale inventar um campo no contrato só para o
 * front ficar bonito. Em vez de um retângulo cinza de "sem imagem", cada evento
 * ganha um gradiente estável derivado do próprio id: o mesmo evento tem sempre a
 * mesma cara, e a grade fica visualmente variada sem nenhum asset.
 */

const PALETTES = [
  ['#7c3aed', '#db2777'],
  ['#0ea5e9', '#6366f1'],
  ['#f59e0b', '#ef4444'],
  ['#10b981', '#0ea5e9'],
  ['#8b5cf6', '#06b6d4'],
  ['#e11d48', '#f59e0b'],
];

function hash(value: string): number {
  let h = 0;
  for (let i = 0; i < value.length; i++) {
    h = (h * 31 + value.charCodeAt(i)) | 0;
  }
  return Math.abs(h);
}

export function artwork(eventId: string) {
  const seed = hash(eventId);
  const [from, to] = PALETTES[seed % PALETTES.length];
  const angle = 120 + (seed % 5) * 25;

  return {
    background: `linear-gradient(${angle}deg, ${from} 0%, ${to} 100%)`,
    accent: from,
  };
}
