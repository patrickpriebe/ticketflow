import type { Ticket } from '../api';
import { Icon } from './Icon';

interface Props {
  tickets: Ticket[];
}

const MODULES = 21;

/**
 * Desenho do QR a partir do payload.
 *
 * Não é um QR Code válido, e não finge ser: gerar um de verdade exige
 * correção de erro Reed-Solomon, e a biblioteca que faz isso pesa mais do que
 * todo o resto desta tela. O payload real é assinado pelo backend e lido na
 * portaria — aqui o que importa é que cada ingresso tenha um desenho próprio e
 * estável, e não um quadrado cinza igual para todos.
 */
function modules(payload: string): boolean[] {
  let state = 2166136261;
  for (let i = 0; i < payload.length; i++) {
    state ^= payload.charCodeAt(i);
    state = Math.imul(state, 16777619);
  }

  const cells: boolean[] = [];
  for (let i = 0; i < MODULES * MODULES; i++) {
    state = (state * 1664525 + 1013904223) % 4294967296;
    cells.push(state / 4294967296 > 0.5);
  }
  return cells;
}

/** Os três olhos do canto, que são o que faz um QR parecer um QR. */
function isFinder(row: number, column: number): boolean | null {
  const corners = [
    [0, 0],
    [0, MODULES - 7],
    [MODULES - 7, 0],
  ];

  for (const [top, left] of corners) {
    const inside = row >= top && row < top + 7 && column >= left && column < left + 7;
    if (!inside) continue;

    const r = row - top;
    const c = column - left;
    const ring = r === 0 || r === 6 || c === 0 || c === 6;
    const core = r >= 2 && r <= 4 && c >= 2 && c <= 4;
    return ring || core;
  }
  return null;
}

function QrGlyph({ payload }: { payload: string }) {
  const cells = modules(payload);

  return (
    <svg viewBox={`0 0 ${MODULES} ${MODULES}`} width="100%" height="100%" aria-hidden="true">
      {cells.map((dark, index) => {
        const row = Math.floor(index / MODULES);
        const column = index % MODULES;
        const finder = isFinder(row, column);
        const on = finder ?? dark;
        return on ? <rect key={index} x={column} y={row} width="1" height="1" fill="#121212" /> : null;
      })}
    </svg>
  );
}

/**
 * Os ingressos emitidos.
 *
 * Até existir a API de leitura no Notification Service, o cliente pagava, a tela
 * dizia "ingressos emitidos" e não havia caminho nenhum para vê-los. Eles ficavam
 * no MongoDB com código e QR, invisíveis.
 */
export function TicketList({ tickets }: Props) {
  if (tickets.length === 0) return null;

  return (
    <div>
      <h3 className="row">
        <Icon name="ticket" size={18} />
        {tickets.length === 1 ? 'Seu ingresso' : `Seus ${tickets.length} ingressos`}
      </h3>

      <ul className="ticket-cards" style={{ marginTop: 'var(--space-3)' }}>
        {tickets.map((ticket) => (
          <li key={ticket.id} className="ticket-card">
            <div className="ticket-card-main">
              <span className="ticket-code">{ticket.ticketCode}</span>
              <span className="muted small">
                {ticket.categoryName}
                {ticket.eventName ? ` · ${ticket.eventName}` : ''}
              </span>
              <span className="muted small">{ticket.holderName}</span>
            </div>

            <div className="qr" title="Apresente este código na portaria">
              <QrGlyph payload={ticket.qrCodePayload} />
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
