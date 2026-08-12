import { linkProps } from '../lib/router';
import { Icon } from './Icon';

export function Footer() {
  return (
    <footer className="site-footer">
      <div className="shell">
        <div className="footer-grid">
          <div>
            <a className="brand" {...linkProps('/')}>
              <span className="brand-mark" aria-hidden="true">
                <Icon name="ticket" size={17} />
              </span>
              TicketFlow
            </a>
            <p className="muted small" style={{ marginTop: 'var(--space-3)', maxWidth: '34ch' }}>
              Projeto de portfólio: um sistema de venda de ingressos distribuído, com
              pedido aceito na hora e pagamento resolvido por eventos.
            </p>
          </div>

          <div>
            <h4>Comprar</h4>
            <ul>
              <li>
                <a {...linkProps('/events')}>Todos os eventos</a>
              </li>
              <li>
                <a {...linkProps('/orders')}>Meus pedidos</a>
              </li>
              <li>Ingressos e QR Code</li>
              <li>Formas de pagamento</li>
            </ul>
          </div>

          <div>
            <h4>Arquitetura</h4>
            <ul>
              <li>Order · Payment · Notification</li>
              <li>Kafka, outbox e idempotência</li>
              <li>PostgreSQL e MongoDB</li>
              <li>Prometheus e Grafana</li>
            </ul>
          </div>

          <div>
            <h4>Projeto</h4>
            <ul>
              <li>
                <a href="https://github.com/patrickpriebe/ticketflow" target="_blank" rel="noreferrer">
                  Código no GitHub
                </a>
              </li>
              <li>Documentação em docs/</li>
              <li>Contratos OpenAPI</li>
            </ul>
          </div>
        </div>

        <div className="footer-bottom">
          <span>TicketFlow · ambiente de demonstração</span>
          <span>Nenhum pagamento real é processado.</span>
        </div>
      </div>
    </footer>
  );
}
