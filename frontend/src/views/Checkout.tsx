import { useState } from 'react';
import type { PaymentMethod, Session } from '../api';
import { Icon, type IconName } from '../components/Icon';
import { Poster } from '../components/Poster';
import { Stepper } from '../components/Stepper';
import { cartTotal, type Cart } from '../lib/cart';
import { longDate, money, time } from '../lib/format';
import { navigate } from '../lib/router';

interface Props {
  cart: Cart;
  session: Session | null;
  placing: boolean;
  error: string | null;
  onConfirm: (method: PaymentMethod) => void;
}

/**
 * Os três métodos que o backend aceita.
 *
 * Cada um é uma `PaymentStrategy` diferente lá dentro: endpoint próprio no
 * gateway e política de retry distinta. O texto de cada painel diz o que
 * realmente acontece — não é enfeite de tela, é o comportamento do serviço.
 */
const METHODS: {
  id: PaymentMethod;
  icon: IconName;
  label: string;
  hint: string;
  detail: string;
}[] = [
  {
    id: 'CREDIT_CARD',
    icon: 'card',
    label: 'Cartão de crédito',
    hint: 'Autorização em segundos',
    detail:
      'Neste ambiente nenhum dado de cartão é pedido nem trafega: o Payment Service conversa com um gateway simulado. Mesmo em produção, o sistema guardaria apenas a bandeira e os quatro últimos dígitos — número completo e CVV não são persistidos, não vão para log e não entram em evento.',
  },
  {
    id: 'PIX',
    icon: 'pix',
    label: 'PIX',
    hint: 'Confirmação em segundos',
    detail:
      'O código copia e cola é emitido pelo provedor depois que o pedido é criado. Enquanto ele não é pago, os ingressos ficam reservados e o prazo corre na tela do pedido.',
  },
  {
    id: 'BOLETO',
    icon: 'barcode',
    label: 'Boleto bancário',
    hint: 'Compensa em até 3 dias úteis',
    detail:
      'A linha digitável sai junto com a confirmação do pedido. Como a compensação demora, o boleto é o método com a janela de reserva mais longa — e o único em que vale a pena sair da tela e voltar depois.',
  },
];

/**
 * A escolha sobrevive à ida ao login.
 *
 * Sem isto, quem escolhe PIX, é mandado para se identificar e volta encontra
 * "cartão de crédito" marcado de novo — e a troca é silenciosa, que é a pior
 * versão desse erro.
 */
const METHOD_KEY = 'ticketflow.method';

export function Checkout({ cart, session, placing, error, onConfirm }: Props) {
  const [method, setMethod] = useState<PaymentMethod>(
    () => (sessionStorage.getItem(METHOD_KEY) as PaymentMethod | null) ?? 'CREDIT_CARD',
  );

  const choose = (next: PaymentMethod) => {
    setMethod(next);
    sessionStorage.setItem(METHOD_KEY, next);
  };

  const total = cartTotal(cart);
  const selected = METHODS.find((option) => option.id === method)!;

  return (
    <section className="shell section">
      <Stepper current={2} />

      <div className="checkout">
        <div>
          <button className="back" onClick={() => navigate(`/events/${cart.eventId}`)}>
            <Icon name="arrow-left" size={16} />
            Voltar para o evento
          </button>

          <h1 style={{ marginBottom: 'var(--space-5)' }}>Forma de pagamento</h1>

          {error && (
            <p className="alert" style={{ marginBottom: 'var(--space-4)' }}>
              <Icon name="close" size={18} />
              {error}
            </p>
          )}

          <div className="stack">
            {METHODS.map((option) => (
              <button
                key={option.id}
                className={`method${option.id === method ? ' chosen' : ''}`}
                aria-label={`Pagar com ${option.label}`}
                aria-pressed={option.id === method}
                onClick={() => choose(option.id)}
              >
                <span className="radio" aria-hidden="true" />
                <span className="method-icon">
                  <Icon name={option.icon} size={18} />
                </span>
                <span className="method-text">
                  <strong>{option.label}</strong>
                  <span className="muted small">{option.hint}</span>
                </span>
              </button>
            ))}
          </div>

          <div className="method-detail">
            <div className="row" style={{ alignItems: 'flex-start', gap: 'var(--space-3)' }}>
              <Icon name="shield" size={18} />
              <p className="small" style={{ color: 'var(--text-secondary)' }}>
                {selected.detail}
              </p>
            </div>
          </div>
        </div>

        <aside className="order-summary">
          <h3>Resumo do pedido</h3>

          <div className="summary-event">
            <div className="summary-thumb">
              <Poster seed={cart.eventId} />
            </div>
            <div>
              <strong>{cart.eventName}</strong>
              <p className="muted small">
                {longDate(cart.startsAt)} · {time(cart.startsAt)}
              </p>
              <p className="muted small">
                {cart.venue}, {cart.city}
              </p>
            </div>
          </div>

          <div className="stack" style={{ gap: 'var(--space-2)' }}>
            {cart.lines.map((line) => (
              <div key={line.categoryId} className="summary-line">
                <span>
                  {line.quantity}× {line.name}
                </span>
                <span>
                  {money({ amount: line.unitPrice.amount * line.quantity, currency: line.unitPrice.currency })}
                </span>
              </div>
            ))}

            {/* Sem taxa de serviço: o backend calcula o total a partir do preço
                do catálogo, e inventar uma linha aqui faria a tela discordar
                do valor que vai ser cobrado. */}
            <div className="summary-line">
              <span>Taxa de serviço</span>
              <span>Isenta</span>
            </div>
          </div>

          <div className="summary-total">
            <span>Total</span>
            <strong>{money(total)}</strong>
          </div>

          <button
            className="btn btn-primary btn-lg btn-block"
            disabled={placing}
            onClick={() => onConfirm(method)}
          >
            {placing ? (
              'Enviando…'
            ) : session ? (
              <>
                <Icon name="check" size={16} />
                Confirmar pedido
              </>
            ) : (
              'Entrar para concluir'
            )}
          </button>

          <p className="fine-print">
            Ao confirmar, os ingressos são reservados na hora e a cobrança é processada
            em seguida. Você acompanha o resultado na próxima tela.
          </p>
        </aside>
      </div>
    </section>
  );
}
