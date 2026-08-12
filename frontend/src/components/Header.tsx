import type { Session } from '../api';

interface Props {
  session: Session | null;
  onHome: () => void;
  onMyOrders: () => void;
  onSignOut: () => void;
}

export function Header({ session, onHome, onMyOrders, onSignOut }: Props) {
  return (
    <header className="site-header">
      <div className="shell header-inner">
        <button className="brand" onClick={onHome}>
          <span className="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M3 9V7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 6v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-6Z" />
              <path d="M14 5v14" strokeDasharray="2 3" />
            </svg>
          </span>
          TicketFlow
        </button>

        {session && (
          <nav className="header-nav">
            <button className="ghost" onClick={onMyOrders}>
              Meus pedidos
            </button>
            <div className="user-chip" title={session.email}>
              <span className="avatar" aria-hidden="true">
                {session.name.charAt(0).toUpperCase()}
              </span>
              <span className="user-name">{session.name}</span>
            </div>
            <button className="ghost" onClick={onSignOut}>
              Sair
            </button>
          </nav>
        )}
      </div>
    </header>
  );
}
