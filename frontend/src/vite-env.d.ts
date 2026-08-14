/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Client id do OAuth do Google. Ausente no ambiente local, e é a ausência
   * que faz a tela de entrar cair no emissor de desenvolvimento.
   */
  readonly VITE_GOOGLE_CLIENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
