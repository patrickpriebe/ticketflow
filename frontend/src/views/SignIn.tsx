import { useEffect, useRef, useState } from 'react';
import { googleClientId } from '../api';
import { Icon } from '../components/Icon';
import { loadGoogleIdentity } from '../lib/google';

interface Props {
  /** Emissor de desenvolvimento — só existe onde não há provedor configurado. */
  onSignIn: (name: string, email: string) => Promise<void>;
  /** ID token vindo do Google. */
  onGoogleCredential: (idToken: string) => Promise<void>;
  /** Texto extra quando a pessoa foi trazida para cá no meio de uma compra. */
  reason?: string;
}

const INTRO =
  'O catálogo é público, mas comprar exige identidade — a API tira quem você é do token, nunca do que o navegador manda no corpo da requisição.';

export function SignIn({ onSignIn, onGoogleCredential, reason }: Props) {
  return googleClientId
    ? <GoogleSignIn onCredential={onGoogleCredential} reason={reason} />
    : <DevSignIn onSignIn={onSignIn} reason={reason} />;
}

/* ------------------------------------------------------------------ *
 * Provedor de verdade
 * ------------------------------------------------------------------ */

function GoogleSignIn({
  onCredential,
  reason,
}: {
  onCredential: (idToken: string) => Promise<void>;
  reason?: string;
}) {
  const slot = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);

  // A callback do Google é registrada uma vez e não pode enxergar uma versão
  // velha de `onCredential`. Guardar numa ref evita reinicializar o widget a
  // cada render — reinicializar faz o botão piscar e às vezes sumir.
  const handler = useRef(onCredential);
  handler.current = onCredential;

  useEffect(() => {
    let cancelled = false;

    loadGoogleIdentity()
      .then((google) => {
        if (cancelled || !slot.current) return;

        google.initialize({
          client_id: googleClientId,
          callback: (response) => {
            handler.current(response.credential).catch(() => {
              setError('Entrou no Google, mas o TicketFlow recusou o token.');
            });
          },
          // Entrar tem que ser um ato, não algo que acontece sozinho ao abrir a
          // página. Sessão iniciada sem clique surpreende, e numa tela de compra
          // surpresa é a última coisa que se quer.
          auto_select: false,
        });

        google.renderButton(slot.current, {
          // Um estilo só para os dois temas, de propósito: o widget é do Google
          // e é montado uma vez, então trocar de tema depois deixaria o botão
          // com a aparência antiga até recarregar a página.
          theme: 'filled_blue',
          size: 'large',
          text: 'continue_with',
          shape: 'pill',
          width: 320,
          locale: 'pt-BR',
        });
      })
      .catch(() => {
        if (!cancelled) setError('Não foi possível carregar o login do Google.');
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <Card reason={reason}>
      <div className="google-slot" ref={slot} />

      {error && (
        <p className="alert" style={{ marginTop: 'var(--space-4)' }}>
          <Icon name="close" size={18} />
          {error}
        </p>
      )}

      <p className="fine-print" style={{ marginTop: 'var(--space-4)' }}>
        O TicketFlow não recebe nem guarda sua senha. O Google devolve um token
        assinado, e os serviços conferem a assinatura, o emissor e se ele foi
        emitido para esta aplicação — só isso vale como identidade. A sessão dura
        uma hora.
      </p>
    </Card>
  );
}

/* ------------------------------------------------------------------ *
 * Ambiente local
 * ------------------------------------------------------------------ */

function DevSignIn({
  onSignIn,
  reason,
}: {
  onSignIn: (name: string, email: string) => Promise<void>;
  reason?: string;
}) {
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
    <Card reason={reason}>
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
        desenvolvimento, desligado em qualquer ambiente publicado. Onde há
        provedor configurado, esta mesma tela mostra o botão do Google — e nada
        mais no front muda.
      </p>
    </Card>
  );
}

function Card({ reason, children }: { reason?: string; children: React.ReactNode }) {
  return (
    <section className="shell">
      <div className="auth-card">
        <span className="brand-mark" aria-hidden="true">
          <Icon name="user" size={17} />
        </span>

        <h1 style={{ marginTop: 'var(--space-4)' }}>Entrar para comprar</h1>
        <p className="muted small" style={{ marginTop: 'var(--space-2)' }}>
          {reason ?? INTRO}
        </p>

        {children}
      </div>
    </section>
  );
}
