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
      // O Payment Service faltava aqui, e o `vercel.json` já o roteava — ou seja,
      // desenvolvimento e produção discordavam sobre quantos serviços existem.
      // Localmente `/api/v1/payments/...` caía no Order Service, que não tem
      // essa rota, e o sintoma era um 500 sem relação aparente com pagamento.
      // Vale para a busca do `client_secret` do Stripe Elements também: ela
      // nunca funcionou nesta máquina, e ninguém percebeu porque o gateway
      // simulado aprova na hora e a tela do cartão quase nunca aparece.
      '/api/v1/payments': {
        target: process.env.VITE_PAYMENTS_URL ?? 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api': {
        target: process.env.VITE_API_URL ?? 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
});
