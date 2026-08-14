/**
 * Tema claro/escuro.
 *
 * Três estados, não dois: `light`, `dark` e `system`. Sem o terceiro, quem usa
 * o computador no escuro à noite e claro de dia fica preso na escolha que fez
 * uma vez — e a maioria das pessoas nunca volta ao botão para corrigir.
 *
 * O atributo `data-theme` no <html> só é escrito quando há escolha explícita.
 * No modo `system` ele sai do DOM e o CSS decide sozinho pelo
 * `prefers-color-scheme`.
 */

export type ThemePreference = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'ticketflow.theme';

export function storedPreference(): ThemePreference {
  const raw = localStorage.getItem(STORAGE_KEY);
  return raw === 'light' || raw === 'dark' ? raw : 'system';
}

export function applyPreference(preference: ThemePreference) {
  const root = document.documentElement;

  // Sem isto a troca anima cada transição de cor declarada no CSS e a página
  // inteira derrete por um quinto de segundo.
  root.classList.add('theme-switching');

  if (preference === 'system') {
    root.removeAttribute('data-theme');
  } else {
    root.setAttribute('data-theme', preference);
  }

  localStorage.setItem(STORAGE_KEY, preference);
  requestAnimationFrame(() => root.classList.remove('theme-switching'));
}
