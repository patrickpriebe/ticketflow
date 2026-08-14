import { useEffect, useRef, useState } from 'react';
import { getOrderPayment } from '../api';
import { loadStripe, stripePublishableKey, type StripeElements } from '../lib/stripe';
import { Icon } from './Icon';

interface Props {
  orderId: string;
}

type Phase = 'waiting' | 'ready' | 'confirming' | 'sent' | 'unavailable' | 'error';

/** Quanto esperar a cobrança aparecer antes de desistir de oferecer o formulário. */
const MAX_ATTEMPTS = 15;
const INTERVAL_MS = 2000;

/**
 * Confirmação do cartão, na página do pedido.
 *
 * <p>A ordem das coisas aqui é a premissa do projeto aparecendo na tela: o
 * pedido já foi aceito e os ingressos já estão reservados. A cobrança é criada
 * depois, por um consumidor de Kafka, então existe uma janela — de milissegundos
 * em regime normal, de mais tempo se o serviço estiver acordando — em que o
 * pedido existe e a cobrança ainda não. Por isso a espera é um estado da tela,
 * com texto próprio, e não um erro.
 *
 * <p>Depois de confirmado, este componente sai do caminho: quem move o pedido
 * para PAID é o webhook do Stripe chegando no Payment Service, que publica o
 * evento. A tela do pedido já acompanha isso sozinha.
 */
export function CardPayment({ orderId }: Props) {
  const slot = useRef<HTMLDivElement>(null);
  const elementsRef = useRef<StripeElements | null>(null);
  const stripeRef = useRef<Awaited<ReturnType<typeof loadStripe>> | null>(null);

  const [phase, setPhase] = useState<Phase>('waiting');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!stripePublishableKey) {
      setPhase('unavailable');
      return;
    }

    let cancelled = false;
    let attempts = 0;
    let timer: number | undefined;

    const mount = async (clientSecret: string) => {
      const stripe = await loadStripe();
      if (cancelled || !slot.current) return;

      const elements = stripe.elements({ clientSecret });
      const element = elements.create('payment');
      element.mount(slot.current);

      stripeRef.current = stripe;
      elementsRef.current = elements;
      setPhase('ready');
    };

    const look = async () => {
      attempts += 1;
      try {
        const payment = await getOrderPayment(orderId);
        if (cancelled) return;

        if (payment?.clientSecret) {
          await mount(payment.clientSecret);
          return;
        }

        // Sem segredo: ou a cobrança ainda não existe, ou já não há o que
        // confirmar. Os dois casos são "não mostre o formulário agora".
        if (attempts >= MAX_ATTEMPTS) setPhase('unavailable');
        else timer = window.setTimeout(look, INTERVAL_MS);
      } catch {
        if (!cancelled) {
          setPhase('error');
          setError('Não foi possível carregar a cobrança.');
        }
      }
    };

    look();

    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
      elementsRef.current = null;
    };
  }, [orderId]);

  const confirm = async () => {
    const stripe = stripeRef.current;
    const elements = elementsRef.current;
    if (!stripe || !elements) return;

    setPhase('confirming');
    setError(null);

    // `if_required` evita o redirecionamento de página inteira quando o cartão
    // não pede 3-D Secure — que é a maioria. Quando pede, o Stripe redireciona
    // e volta para esta mesma URL.
    const result = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: window.location.href },
      redirect: 'if_required',
    });

    if (result.error) {
      setPhase('ready');
      setError(result.error.message ?? 'A operadora não autorizou a cobrança.');
      return;
    }

    // Daqui em diante quem resolve é o webhook. A página do pedido já está
    // acompanhando o status e vai mudar sozinha.
    setPhase('sent');
  };

  if (phase === 'unavailable') return null;

  return (
    <section className="card-payment">
      <h3>
        <Icon name="card" size={18} />
        Pagar com cartão
      </h3>

      {phase === 'waiting' && (
        <p className="muted small">Preparando a cobrança…</p>
      )}

      {phase === 'sent' ? (
        <p className="muted small">
          Cartão enviado para a operadora. Assim que ela responder, este pedido
          muda de status sozinho — pode deixar a página aberta.
        </p>
      ) : (
        <>
          <div ref={slot} className="card-slot" />

          {phase !== 'waiting' && (
            <button
              className="btn btn-primary btn-lg btn-block"
              disabled={phase !== 'ready'}
              onClick={confirm}
            >
              {phase === 'confirming' ? 'Confirmando…' : 'Confirmar pagamento'}
            </button>
          )}

          <p className="fine-print">
            O número do cartão vai direto para o Stripe, num campo que é dele. O
            TicketFlow não recebe, não guarda e não registra esse dado — só a
            bandeira e os últimos quatro dígitos.
          </p>
        </>
      )}

      {error && (
        <p className="alert" style={{ marginTop: 'var(--space-4)' }}>
          <Icon name="close" size={18} />
          {error}
        </p>
      )}
    </section>
  );
}
