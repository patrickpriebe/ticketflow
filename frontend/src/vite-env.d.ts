/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Client id do OAuth do Google. Ausente no ambiente local, e é a ausência
   * que faz a tela de entrar cair no emissor de desenvolvimento.
   */
  readonly VITE_GOOGLE_CLIENT_ID?: string;
  /**
   * Chave publicável do Stripe. Ausente, a tela do pedido não oferece
   * confirmação de cartão — é o caso do ambiente local com gateway simulado,
   * onde o cartão resolve sem navegador no meio.
   */
  readonly VITE_STRIPE_PUBLISHABLE_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
