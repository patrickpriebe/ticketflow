import type { Ticket } from '../api';

interface Props {
  tickets: Ticket[];
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
    <div className="tickets">
      <h3>
        {tickets.length === 1 ? 'Seu ingresso' : `Seus ${tickets.length} ingressos`}
      </h3>

      <ul className="ticket-cards">
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

            {/* Marcador de QR: o payload real é assinado pelo backend e lido na
                portaria. Desenhar um QR de verdade exigiria uma biblioteca para
                pouco ganho num projeto de portfólio. */}
            <div className="qr" title={ticket.qrCodePayload} aria-hidden="true">
              <span /><span /><span />
              <span /><span /><span />
              <span /><span /><span />
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
