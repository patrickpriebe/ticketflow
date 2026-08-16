import { useEffect, useState } from 'react';
import { getOrderPayment, type OrderPayment } from './api';

/**
 * Acompanha o destino do dinheiro de um pedido cancelado.
 *
 * Existe porque *cancelado* sozinho é ambíguo: não diz se alguém chegou a ser
 * cobrado. A resposta mora no Payment Service, e o navegador pergunta direto a
 * ele — o Order Service não pode, porque serviço não fala com serviço aqui.
 *
 * **O estorno é assíncrono.** O pedido vira CANCELLED na hora; a devolução só
 * acontece quando o Payment Service consome o `ORDER_CANCELLED`. Uma consulta
 * única pegaria a cobrança ainda APPROVED e a tela ficaria dizendo "processando"
 * para sempre. Por isso a consulta se repete enquanto houver estorno a caminho,
 * e para sozinha ao chegar num desfecho — mesma disciplina do `useOrderStatus`.
 *
 * O teto de tentativas existe para o caso que ninguém quer: o provedor recusando
 * o estorno. Aí a cobrança fica APPROVED para sempre, e insistir seria consultar
 * até a aba fechar.
 */
const SETTLED = ['REFUNDED', 'CANCELLED', 'REJECTED', 'FAILED'];
const MAX_ATTEMPTS = 12;
const INTERVAL_MS = 3000;

export function useRefundStatus(orderId: string | null, enabled: boolean) {
  const [payment, setPayment] = useState<OrderPayment | null>(null);
  /** Verdadeiro enquanto a cobrança existe e ainda não teve desfecho. */
  const [pending, setPending] = useState(false);
  /**
   * A consulta falhou e não sabemos o que houve com o dinheiro.
   *
   * Distinto de "não há cobrança". A primeira versão tratava erro como ausência
   * e a tela dizia "nenhuma cobrança foi feita" — afirmação categórica baseada
   * em nada, e justamente sobre a pergunta que a pessoa veio fazer.
   */
  const [unknown, setUnknown] = useState(false);

  useEffect(() => {
    if (!orderId || !enabled) {
      setPayment(null);
      setPending(false);
      setUnknown(false);
      return;
    }

    let cancelled = false;
    let timer: number | undefined;
    let attempts = 0;

    const look = async () => {
      attempts++;
      try {
        const current = await getOrderPayment(orderId);
        if (cancelled) return;

        setPayment(current);

        // Sem cobrança: nada foi cobrado e nada vai ser. Não há o que esperar —
        // a linha que impede a cobrança de nascer já foi gravada.
        if (!current) {
          setPending(false);
          return;
        }

        // Já estornada conta como resolvida mesmo com status CANCELLED — é o
        // caso da cobrança que cruzou o cancelamento em voo.
        const settled = current.refunded || SETTLED.includes(current.status);
        setPending(!settled);
        if (settled || attempts >= MAX_ATTEMPTS) return;

        timer = window.setTimeout(look, INTERVAL_MS);
      } catch {
        // A tela do pedido cancelado não vira tela de erro — o cancelamento
        // aconteceu de qualquer forma. Mas também não inventa: dizer "nenhuma
        // cobrança foi feita" sem ter conseguido perguntar seria afirmar
        // exatamente o que não se sabe.
        if (cancelled) return;
        setPending(false);
        setUnknown(true);
      }
    };

    look();

    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [orderId, enabled]);

  return { payment, pending, unknown };
}
