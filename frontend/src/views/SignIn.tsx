import { useState } from 'react';
import { Icon } from '../components/Icon';

interface Props {
  onSignIn: (name: string, email: string) => Promise<void>;
  /** Texto extra quando a pessoa foi trazida para cá no meio de uma compra. */
  reason?: string;
}

export function SignIn({ onSignIn, reason }: Props) {
  const [name, setName] = useState('Ana Souza');
  const [email, setEmail] = useState('ana.souza@example.com');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSignIn(name, email);
    } catch {
      setError('Não foi possível entrar. O Order Service está no ar?');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="shell">
      <div className="auth-card">
        <span className="brand-mark" aria-hidden="true">
          <Icon name="user" size={17} />
        </span>

        <h1 style={{ marginTop: 'var(--space-4)' }}>Entrar para comprar</h1>
        <p className="muted small" style={{ marginTop: 'var(--space-2)' }}>
          {reason ??
            'O catálogo é público, mas comprar exige identidade — a API tira quem você é do token, nunca do que o navegador manda no corpo da requisição.'}
        </p>

        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="signin-name">Nome</label>
            <input
              id="signin-name"
              className="input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              minLength={2}
            />
          </div>

          <div className="field">
            <label htmlFor="signin-email">E-mail</label>
            <input
              id="signin-email"
              className="input"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <button className="btn btn-primary btn-lg btn-block" disabled={busy}>
            {busy ? 'Entrando…' : 'Entrar'}
          </button>
        </form>

        {error && (
          <p className="alert" style={{ marginTop: 'var(--space-4)' }}>
            <Icon name="close" size={18} />
            {error}
          </p>
        )}

        <p className="fine-print" style={{ marginTop: 'var(--space-4)' }}>
          Ambiente local: o token é emitido sem senha por um endpoint de
          desenvolvimento. Num ambiente real isto seria o redirecionamento para o
          provedor de identidade — e nada mais no front mudaria.
        </p>
      </div>
    </section>
  );
}
