import { useSyncExternalStore } from 'react';

/**
 * Pele visual: qual dos desenhos o site veste.
 *
 * É um eixo separado do claro/escuro, e não mais opções dentro dele. As duas
 * perguntas são independentes — quem prefere o desenho antigo pode querer o
 * tema escuro do mesmo jeito — e juntar as duas num seletor só daria uma lista
 * de itens que não significam a mesma coisa.
 *
 * - `boxoffice` — bilheteria: papel osso, latão, letra de marquise, cartão de
 *   evento em forma de ingresso. É o padrão.
 * - `stage` — 1a "Palco": Geist, imagem grande, número monoespaçado, âmbar
 *   reservado para urgência. Marketplace de larga escala.
 * - `poster` — 1b "Cartaz": Instrument Serif nos títulos, muito branco,
 *   catálogo lido como índice, filete no lugar da caixa.
 * - `classic` — o desenho original: azul de marca, Inter, cartão retangular
 *   com selo de calendário sobre a arte.
 *
 * O atributo `data-skin` no <html> é escrito sempre, inclusive para o padrão.
 * Diferente do tema, aqui não existe um estado "o que o sistema mandar": o
 * sistema operacional não tem opinião sobre qual desenho a pessoa prefere.
 *
 * Quase tudo se resolve em CSS, porque todas as peles compartilham os nomes
 * dos tokens. O que não se resolve são as duas telas cuja *estrutura* muda —
 * o cartão de evento e a abertura da home — e para essas o React precisa saber
 * a pele. Daí a assinatura de store: `useSkin` lê o mesmo valor que o CSS está
 * usando, sem um segundo estado que possa divergir dele.
 */

export type Skin = 'boxoffice' | 'stage' | 'poster' | 'classic';

const ALL: Skin[] = ['boxoffice', 'stage', 'poster', 'classic'];

export const SKINS: { value: Skin; label: string; hint: string }[] = [
  { value: 'boxoffice', label: 'Bilheteria', hint: 'Papel, latão e o cartão em forma de ingresso' },
  { value: 'stage', label: 'Palco', hint: 'Geist, imagem grande e número monoespaçado' },
  { value: 'poster', label: 'Cartaz', hint: 'Serifa nos títulos, muito branco e filete fino' },
  { value: 'classic', label: 'Clássico', hint: 'O desenho original, em azul de marca' },
];

/**
 * Quais peles usam a marcação retangular em vez do ingresso com canhoto.
 *
 * Palco e Cartaz nasceram como redesenhos do layout original — mesmas rotas,
 * componentes refeitos —, então herdam a estrutura do clássico e mudam só de
 * roupa. O canhoto destacável é assinatura do desenho de bilheteria e não faz
 * sentido fora dele.
 */
export function usesFlatLayout(skin: Skin): boolean {
  return skin !== 'boxoffice';
}

const STORAGE_KEY = 'ticketflow.skin';

const listeners = new Set<() => void>();

function isSkin(value: string | null): value is Skin {
  return value !== null && (ALL as string[]).includes(value);
}

function read(): Skin {
  // A fonte da verdade é o atributo, não o localStorage: quem escreve o
  // atributo antes da primeira pintura é o theme-init.js, e ler dele garante
  // que o React e o CSS nunca discordem sobre qual pele está no ar.
  const attr = document.documentElement.getAttribute('data-skin');
  return isSkin(attr) ? attr : 'boxoffice';
}

export function applySkin(skin: Skin) {
  const root = document.documentElement;

  // Mesmo motivo do tema: sem isto cada transição de cor declarada no CSS
  // dispara junto e a página inteira derrete por um quinto de segundo. Aqui é
  // pior ainda, porque a troca de pele muda também a fonte e o raio de canto.
  root.classList.add('theme-switching');
  root.setAttribute('data-skin', skin);

  try {
    localStorage.setItem(STORAGE_KEY, skin);
  } catch {
    /* sem armazenamento: a escolha vale só enquanto a aba estiver aberta */
  }

  requestAnimationFrame(() => root.classList.remove('theme-switching'));
  listeners.forEach((notify) => notify());
}

function subscribe(notify: () => void) {
  listeners.add(notify);
  return () => {
    listeners.delete(notify);
  };
}

/** A pele em vigor, para os poucos componentes cuja estrutura muda com ela. */
export function useSkin(): Skin {
  return useSyncExternalStore(subscribe, read, () => 'boxoffice' as Skin);
}
