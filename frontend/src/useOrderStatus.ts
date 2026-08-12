import { useEffect, useState } from 'react';
import { getOrder, ProblemError, type Order } from './api';

/**
 * Acompanha um pedido até ele sair de PENDING.
 *
 * Polling, não WebSocket, porque é o que o contrato da API define: o
 * `POST /orders` responde 202 e o cliente consulta `GET /orders/{id}`. Trocar por
 * push exigiria SSE no Order Service — está anotado como próximo passo, não
 * simulado aqui.
 *
 * O intervalo para sozinho quando o pedido chega a um estado final, para a aba não
 * ficar batendo na API para sempre.
 */
export function useOrderStatus(orderId: string | null, intervalMs = 2000) {
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!orderId) {
      setOrder(null);
      return;
    }

    let cancelled = false;
    let timer: number | undefined;

    const poll = async () => {
      try {
        const current = await getOrder(orderId);
        if (cancelled) return;

        setOrder(current);
        setError(null);

        if (current.status === 'PENDING') {
          timer = window.setTimeout(poll, intervalMs);
        }
      } catch (e) {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : 'Falha ao consultar o pedido');

        // 404 e 401 são respostas definitivas: o pedido não é desta pessoa, ou
        // não existe. Insistir só gera requisição para sempre — foi o que
        // aconteceu ao abrir um pedido de outro cliente, com o console enchendo
        // de 404 a cada dois segundos.
        const definitive = e instanceof ProblemError && (e.status === 404 || e.status === 401);
        if (definitive) return;

        // Erro de rede é outra coisa: aí sim vale tentar de novo, mais devagar.
        timer = window.setTimeout(poll, intervalMs * 2);
      }
    };

    poll();

    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [orderId, intervalMs]);

  return { order, error };
}
