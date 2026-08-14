/**
 * Google Identity Services — só o necessário para o botão de entrar.
 *
 * O fluxo escolhido é o do **ID token**: o Google autentica a pessoa e devolve
 * um JWT assinado direto para o navegador, que o manda no `Authorization` de
 * cada requisição. Não há troca de código no backend, não há sessão, não há
 * cookie — os dois serviços continuam sendo resource servers puros, exatamente
 * como eram com o token local.
 *
 * O custo dessa escolha é honesto: o ID token vale uma hora e não vem com
 * refresh token. Passada a hora, a API responde 401, o front derruba a sessão e
 * a pessoa entra de novo. Para sessão longa o caminho é o Authorization Code
 * com PKCE, que traz um backend de autenticação junto — mais peça do que este
 * projeto precisa para demonstrar o ponto.
 */

const SCRIPT_SRC = 'https://accounts.google.com/gsi/client';

export interface GoogleCredentialResponse {
  credential: string;
}

interface GoogleIdApi {
  initialize(config: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    auto_select?: boolean;
    cancel_on_tap_outside?: boolean;
    use_fedcm_for_prompt?: boolean;
  }): void;
  renderButton(
    parent: HTMLElement,
    options: {
      theme?: 'outline' | 'filled_blue' | 'filled_black';
      size?: 'small' | 'medium' | 'large';
      text?: 'signin_with' | 'signup_with' | 'continue_with';
      shape?: 'rectangular' | 'pill';
      width?: number;
      locale?: string;
    },
  ): void;
  disableAutoSelect(): void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleIdApi } };
  }
}

let pending: Promise<GoogleIdApi> | null = null;

/**
 * Carrega o script do Google uma vez só.
 *
 * O script fica fora do `index.html` de propósito: no ambiente local não há
 * client id, e carregar um script de terceiro em toda visita para nunca usá-lo
 * é peso e é um pedido a mais para fora que ninguém pediu.
 */
export function loadGoogleIdentity(): Promise<GoogleIdApi> {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id);
  if (pending) return pending;

  pending = new Promise<GoogleIdApi>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_SRC}"]`);
    const script = existing ?? document.createElement('script');

    script.addEventListener('load', () => {
      const api = window.google?.accounts?.id;
      if (api) resolve(api);
      else reject(new Error('o script do Google carregou sem a API esperada'));
    });
    script.addEventListener('error', () => {
      // Zera para uma próxima tentativa poder acontecer: sem isto, uma falha de
      // rede momentânea deixaria o botão morto até recarregar a página.
      pending = null;
      reject(new Error('não foi possível carregar o Google'));
    });

    if (!existing) {
      script.src = SCRIPT_SRC;
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }
  });

  return pending;
}

/** Impede o Google de reentrar sozinho depois que a pessoa sai. */
export function forgetGoogleSession() {
  window.google?.accounts?.id?.disableAutoSelect();
}
