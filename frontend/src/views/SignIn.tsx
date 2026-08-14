import { useEffect, useRef, useState } from 'react';
import { googleClientId } from '../api';
import { Icon } from '../components/Icon';
import { Poster } from '../components/Poster';
import { loadGoogleIdentity } from '../lib/google';
import { linkProps } from '../lib/router';

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
  return (
    <section className="auth-split">
      <Brand reason={reason} />

      <div className="auth-panel">
        {googleClientId
          ? <GoogleSignIn onCredential={onGoogleCredential} />
          : <DevSignIn onSignIn={onSignIn} />}
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------ *
 * O lado que explica
 *
 * Uma tela de entrar costuma ser um retângulo no meio do vazio. Aqui ela
 * carrega o mesmo peso visual do resto do site — a arte é a mesma do hero,
 * gerada do mesmo jeito — e responde à pergunta que a pessoa realmente tem
 * neste momento: o que acontece depois que eu entrar.
 * ------------------------------------------------------------------ */

function Brand({ reason }: { reason?: string }) {
  return (
    <aside className="auth-brand">
      {/* O MESMO seed do hero da home, e não um só desta tela.
          A paleta sai de um hash do seed, então um seed próprio daqui cairia
          numa cor por sorteio — o primeiro que tentei deu verde, brigando com o
          azul da marca. Repetir o seed do hero é explícito: entrar veste a arte
          da home, e nenhuma mudança futura na lista de paletas transforma esta
          tela em outra coisa sem querer. */}
      <div className="hero-art">
        <Poster seed="ticketflow-hero" />
      </div>

      <div className="auth-brand-body">
        <h1>Entre para garantir seu ingresso</h1>
        <p>{reason ?? INTRO}</p>

        <ul className="auth-points">
          <li>
            <span className="auth-point-icon" aria-hidden="true">
              <Icon name="bolt" size={17} />
            </span>
            <div>
              <strong>O pedido é aceito na hora</strong>
              <span>
                O pagamento resolve em segundo plano e você acompanha o status —
                sem tela de espera enquanto a operadora responde.
              </span>
            </div>
          </li>

          <li>
            <span className="auth-point-icon" aria-hidden="true">
              <Icon name="shield" size={17} />
            </span>
            <div>
              <strong>Sua senha não passa por aqui</strong>
              <span>
                Quem autentica é o Google. O TicketFlow só confere a assinatura do
                token que ele devolve.
              </span>
            </div>
          </li>

          <li>
            <span className="auth-point-icon" aria-hidden="true">
              <Icon name="ticket" size={17} />
            </span>
            <div>
              <strong>O ingresso aparece em Meus pedidos</strong>
              <span>
                Assim que o pagamento é aprovado, com QR Code e tudo o que você
                precisa na porta.
              </span>
            </div>
          </li>
        </ul>
      </div>
    </aside>
  );
}

/* ------------------------------------------------------------------ *
 * Provedor de verdade
 * ------------------------------------------------------------------ */

function GoogleSignIn({ onCredential }: { onCredential: (idToken: string) => Promise<void> }) {
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
    <Card
      title="Entrar"
      subtitle="Use sua conta Google. É o único jeito de entrar por aqui."
      footnote="A sessão dura uma hora — é o tempo de vida do token que o Google emite. Depois disso é só entrar de novo."
    >
      <div className="google-slot" ref={slot} />
      {error && <Alert>{error}</Alert>}
    </Card>
  );
}

/* ------------------------------------------------------------------ *
 * Ambiente local
 * ------------------------------------------------------------------ */

function DevSignIn({ onSignIn }: { onSignIn: (name: string, email: string) => Promise<void> }) {
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
    <Card
      title="Entrar"
      subtitle="Ambiente local: o token é emitido sem senha, e qualquer nome serve."
      footnote="Este emissor está desligado em qualquer ambiente publicado. Onde há provedor configurado, esta mesma tela mostra o botão do Google — e nada mais no front muda."
    >
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

      {error && <Alert>{error}</Alert>}
    </Card>
  );
}

/* ------------------------------------------------------------------ *
 * Moldura
 * ------------------------------------------------------------------ */

function Card({
  title,
  subtitle,
  footnote,
  children,
}: {
  title: string;
  subtitle: string;
  footnote: string;
  children: React.ReactNode;
}) {
  return (
    <div className="auth-card">
      <span className="brand-mark" aria-hidden="true">
        <Icon name="user" size={17} />
      </span>

      <h2>{title}</h2>
      <p className="muted small">{subtitle}</p>

      {children}

      <p className="fine-print">{footnote}</p>

      <a className="auth-back" {...linkProps('/events')}>
        <Icon name="chevron" size={15} />
        Voltar para o catálogo
      </a>
    </div>
  );
}

function Alert({ children }: { children: React.ReactNode }) {
  return (
    <p className="alert" style={{ marginTop: 'var(--space-4)' }}>
      <Icon name="close" size={18} />
      {children}
    </p>
  );
}
