import { useState } from 'react';

interface Props {
  onSignIn: (name: string, email: string) => Promise<void>;
}

export function SignIn({ onSignIn }: Props) {
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
    <section className="shell section narrow">
      <div className="signin-card">
        <h2>Entrar para comprar</h2>
        <p className="muted">
          O catálogo é público, mas comprar exige identidade — a API tira quem você é
          do token, nunca do que o navegador manda no corpo da requisição.
        </p>

        <form onSubmit={submit}>
          <label>
            Nome
            <input value={name} onChange={(e) => setName(e.target.value)} required minLength={2} />
          </label>
          <label>
            E-mail
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </label>

          <button className="primary block" disabled={busy}>
            {busy ? 'Entrando…' : 'Entrar'}
          </button>
        </form>

        {error && <p className="alert soft">{error}</p>}

        <p className="fine-print">
          Ambiente local: o token é emitido sem senha por um endpoint de
          desenvolvimento. Num ambiente real isto seria o redirecionamento para o
          provedor de identidade — e nada mais no front mudaria.
        </p>
      </div>
    </section>
  );
}
