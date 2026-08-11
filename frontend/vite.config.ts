import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Proxy em vez de CORS no backend: em produção o front seria servido pelo
    // mesmo domínio, e abrir CORS só para o ambiente de desenvolvimento é uma
    // configuração que costuma vazar para produção por esquecimento.
    proxy: {
      '/api': {
        target: process.env.VITE_API_URL ?? 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
});
