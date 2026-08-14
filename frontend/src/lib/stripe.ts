/**
 * Stripe.js — só o necessário para confirmar um cartão.
 *
 * Os tipos são escritos à mão em vez de instalar `@stripe/stripe-js`, pela mesma
 * razão do Google Identity: este front não tem dependência além de React, e a
 * superfície usada aqui são quatro funções. Um pacote a mais no `package.json`
 * custaria mais atenção do que estas trinta linhas.
 *
 * **O número do cartão nunca passa pelo TicketFlow.** O campo é um iframe do
 * próprio Stripe; o que o nosso código toca é o `client_secret`, que autoriza
 * confirmar aquela cobrança específica e nada mais.
 */

const SCRIPT_SRC = 'https://js.stripe.com/v3/';

export const stripePublishableKey: string =
  (import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? '').trim();

export interface StripeElement {
  mount(container: HTMLElement): void;
  unmount(): void;
  destroy(): void;
}

export interface StripeElements {
  create(type: 'payment', options?: Record<string, unknown>): StripeElement;
}

export interface Stripe {
  elements(options: { clientSecret: string; appearance?: Record<string, unknown> }): StripeElements;
  confirmPayment(options: {
    elements: StripeElements;
    confirmParams?: Record<string, unknown>;
    redirect?: 'if_required' | 'always';
  }): Promise<{ error?: { message?: string; type?: string } }>;
}

declare global {
  interface Window {
    Stripe?: (key: string) => Stripe;
  }
}

let pending: Promise<Stripe> | null = null;

/** Carrega o script uma vez só, e só onde há chave configurada. */
export function loadStripe(): Promise<Stripe> {
  if (!stripePublishableKey) {
    return Promise.reject(new Error('sem chave publicável do Stripe'));
  }
  if (window.Stripe) return Promise.resolve(window.Stripe(stripePublishableKey));
  if (pending) return pending;

  pending = new Promise<Stripe>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_SRC}"]`);
    const script = existing ?? document.createElement('script');

    script.addEventListener('load', () => {
      if (window.Stripe) resolve(window.Stripe(stripePublishableKey));
      else reject(new Error('o script do Stripe carregou sem a API esperada'));
    });
    script.addEventListener('error', () => {
      // Zera para permitir uma nova tentativa: sem isto, uma falha de rede
      // momentânea deixaria o formulário morto até recarregar a página.
      pending = null;
      reject(new Error('não foi possível carregar o Stripe'));
    });

    if (!existing) {
      script.src = SCRIPT_SRC;
      script.async = true;
      document.head.appendChild(script);
    }
  });

  return pending;
}
