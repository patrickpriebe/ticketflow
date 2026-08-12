import { useState } from 'react';
import type { Session } from '../api';
import { linkProps, navigate, type Route } from '../lib/router';
import { Icon } from './Icon';
import { ThemeToggle } from './ThemeToggle';

interface Props {
  session: Session | null;
  route: Route;
  onSignOut: () => void;
}

export function Header({ session, route, onSignOut }: Props) {
  const [term, setTerm] = useState('');

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const query = term.trim();
    navigate(query ? `/events?q=${encodeURIComponent(query)}` : '/events');
  };

  return (
    <header className="site-header">
      <div className="shell header-inner">
        <a className="brand" {...linkProps('/')}>
          <span className="brand-mark" aria-hidden="true">
            <Icon name="ticket" size={17} />
          </span>
          TicketFlow
        </a>

        <nav className="header-nav">
          <a className={navClass(route, 'events')} {...linkProps('/events')}>
            Descobrir
          </a>
          {session && (
            <a className={navClass(route, 'orders')} {...linkProps('/orders')}>
              Meus pedidos
            </a>
          )}
        </nav>

        <div className="header-spacer" />

        <form className="header-search" onSubmit={submit} role="search">
          <div className="search">
            <Icon name="search" />
            <input
              type="search"
              value={term}
              placeholder="Buscar eventos, locais ou cidades"
              aria-label="Buscar eventos"
              onChange={(e) => setTerm(e.target.value)}
            />
          </div>
        </form>

        <div className="header-actions">
          <ThemeToggle />

          {session ? (
            <>
              <span className="user-chip" title={session.email}>
                <span className="avatar" aria-hidden="true">
                  {session.name.charAt(0).toUpperCase()}
                </span>
                {session.name.split(' ')[0]}
              </span>
              <button className="btn btn-ghost btn-sm" onClick={onSignOut}>
                Sair
              </button>
            </>
          ) : (
            <a className="btn btn-primary btn-sm" {...linkProps('/signin')}>
              Entrar
            </a>
          )}
        </div>
      </div>
    </header>
  );
}

function navClass(route: Route, name: Route['name']) {
  // A página do evento continua sendo "Descobrir" para efeito de navegação: é
  // de lá que se chega nela, e apagar o realce quando a pessoa entra num evento
  // faz o menu parecer que perdeu o lugar.
  const active = route.name === name || (name === 'events' && route.name === 'event');
  return `nav-link${active ? ' active' : ''}`;
}
