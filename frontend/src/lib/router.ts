/**
 * Roteador mínimo sobre a History API.
 *
 * Não é uma dependência a menos por economia: é que um front deste tamanho usa
 * uma fração do React Router, e a parte que ele usa cabe em cinquenta linhas
 * legíveis. O que não cabe — rotas aninhadas, carregamento por rota, guardas —
 * também não é necessário aqui.
 *
 * O importante é que exista URL de verdade: o botão voltar funciona, um pedido
 * pode ser aberto direto pelo link e recarregar a página não joga a pessoa para
 * a home. Uma tela de compra sem isso é frustrante de um jeito difícil de
 * explicar e fácil de sentir.
 */

import { useSyncExternalStore } from 'react';

export type Route =
  | { name: 'home' }
  | { name: 'events'; query: URLSearchParams }
  | { name: 'event'; id: string }
  | { name: 'checkout' }
  | { name: 'orders' }
  | { name: 'order'; id: string }
  | { name: 'signin' }
  | { name: 'notFound' };

const listeners = new Set<() => void>();

function notify() {
  listeners.forEach((listener) => listener());
}

export function navigate(path: string, options?: { replace?: boolean }) {
  if (path === location.pathname + location.search) return;
  if (options?.replace) history.replaceState(null, '', path);
  else history.pushState(null, '', path);
  window.scrollTo({ top: 0 });
  notify();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  window.addEventListener('popstate', notify);
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) window.removeEventListener('popstate', notify);
  };
}

const snapshot = () => location.pathname + location.search;

export function useRoute(): Route {
  return parse(useSyncExternalStore(subscribe, snapshot));
}

export function parse(url: string): Route {
  const [pathname, search] = url.split('?');
  const segments = pathname.split('/').filter(Boolean);

  if (segments.length === 0) return { name: 'home' };

  const [head, tail] = segments;

  if (head === 'events') {
    return tail ? { name: 'event', id: tail } : { name: 'events', query: new URLSearchParams(search) };
  }
  if (head === 'orders') {
    return tail ? { name: 'order', id: tail } : { name: 'orders' };
  }
  if (head === 'checkout' && !tail) return { name: 'checkout' };
  if (head === 'signin' && !tail) return { name: 'signin' };

  return { name: 'notFound' };
}

/**
 * Âncora que navega sem recarregar. Continua sendo um `<a href>`: abrir em nova
 * aba, copiar o endereço e o leitor de tela anunciando "link" só funcionam
 * porque é um link de verdade, e não um `<div onClick>`.
 */
export function linkProps(to: string) {
  return {
    href: to,
    onClick(event: React.MouseEvent<HTMLAnchorElement>) {
      // Ctrl/Cmd/clique do meio abrem em outra aba — o navegador cuida disso.
      if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) {
        return;
      }
      event.preventDefault();
      navigate(to);
    },
  };
}
