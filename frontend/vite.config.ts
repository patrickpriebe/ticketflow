import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Proxy em vez de CORS no backend: em produção o front seria servido pelo
    // mesmo domínio, e abrir CORS só para o ambiente de desenvolvimento é uma
    // configuração que costuma vazar para produção por esquecimento.
    // A ordem importa: a regra mais específica vem primeiro, senão `/api` engole
    // tudo. Os ingressos moram no Notification Service, não no Order Service —
    // cada serviço é dono dos próprios dados, e o front fala com os dois.
    // Em produção isso seria um gateway ou o mesmo domínio com rotas.
    proxy: {
      '/api/v1/tickets': {
        target: process.env.VITE_TICKETS_URL ?? 'http://localhost:8083',
        changeOrigin: true,
      },
      '/api': {
        target: process.env.VITE_API_URL ?? 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
});
