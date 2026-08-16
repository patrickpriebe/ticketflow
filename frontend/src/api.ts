/**
 * Cliente do Order Service.
 *
 * Os tipos espelham `contracts/openapi/order-service.yaml`. Não são gerados a
 * partir dele — geração traria um passo de build para um front deste tamanho — mas
 * o contrato é a fonte da verdade, e divergência aqui é bug.
 */

export type OrderStatus = 'PENDING' | 'PAID' | 'REJECTED' | 'CANCELLED' | 'EXPIRED';

export interface Money {
  amount: number;
  currency: string;
}

export interface EventSummary {
  id: string;
  name: string;
  venue: string;
  city: string;
  startsAt: string;
  status: string;
  priceFrom?: Money;
}

export interface TicketCategory {
  id: string;
  name: string;
  price: Money;
  availableQuantity: number;
}

export interface EventDetail extends EventSummary {
  /** Opcional no contrato: nem todo evento do catálogo tem texto de apresentação. */
  description?: string;
  salesStartAt: string;
  salesEndAt: string;
  categories: TicketCategory[];
}

export interface OrderStatusChange {
  fromStatus?: OrderStatus;
  toStatus: OrderStatus;
  reason?: string;
  occurredAt: string;
}

export interface Order {
  id: string;
  status: OrderStatus;
  customer: { id: string; name: string; email: string };
  paymentMethod: string;
  items: { categoryName: string; quantity: number; unitPrice: Money; subtotal: Money }[];
  totalAmount: Money;
  /** Prazo para o pagamento chegar. Depois disso o job de expiração devolve os ingressos ao estoque. */
  expiresAt: string | null;
  statusHistory: OrderStatusChange[];
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: { totalElements: number };
}

/** RFC 7807: o backend responde `application/problem+json` em todo erro. */
export class ProblemError extends Error {
  constructor(readonly title: string, readonly detail: string, readonly status: number) {
    super(detail || title);
  }
}

/* ------------------------------------------------------------------ *
 * Sessão
 *
 * O token guarda quem é o cliente. A API extrai a identidade do `sub`, e
 * nenhuma requisição manda `customerId` — mandar de volta reabriria o buraco
 * que a autenticação fechou.
 * ------------------------------------------------------------------ */

const TOKEN_KEY = 'ticketflow.token';

export interface Session {
  token: string;
  name: string;
  email: string;
  /**
   * Só o emissor local devolve isto, e nada no front depende dele.
   *
   * Quem manda no `customerId` é o backend, que o deriva do token — o front não
   * tem como calcular o mesmo valor a partir de um token do Google, e não
   * precisa. Se um dia precisar, o certo é a API expor, não o navegador
   * adivinhar.
   */
  customerId?: string;
}

/**
 * O client id do OAuth, quando existe.
 *
 * Não é segredo: ele viaja no HTML de qualquer site que use "entrar com
 * Google", e é por isso que o backend confere o `aud` do token em vez de
 * confiar em quem chamou.
 *
 * Vazio no ambiente local, e é o que faz a tela de entrar cair no emissor de
 * desenvolvimento — o projeto continua rodando para quem clona o repositório
 * sem ter conta em provedor nenhum.
 */
export const googleClientId: string = (import.meta.env.VITE_GOOGLE_CLIENT_ID ?? '').trim();

let session: Session | null = null;

export function currentSession(): Session | null {
  if (session) return session;
  const stored = localStorage.getItem(TOKEN_KEY);
  if (!stored) return null;
  try {
    session = JSON.parse(stored) as Session;
    return session;
  } catch {
    localStorage.removeItem(TOKEN_KEY);
    return null;
  }
}

/**
 * "Entrar" no ambiente local.
 *
 * Chama o emissor de desenvolvimento, que não verifica nada. Num ambiente real
 * isto seria o redirecionamento para o provedor de identidade — o resto do
 * front não mudaria, porque só conhece um token opaco.
 */
export async function signIn(name: string, email: string): Promise<Session> {
  const response = await fetch('/api/dev/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, email }),
  });
  if (!response.ok) throw new Error('Não foi possível entrar');

  const issued = await response.json();
  session = {
    token: issued.token,
    customerId: issued.customerId,
    name: issued.name,
    email: issued.email,
  };
  localStorage.setItem(TOKEN_KEY, JSON.stringify(session));
  return session;
}

/**
 * Adota o ID token que o Google acabou de emitir.
 *
 * O token vai inteiro para o backend no `Authorization`, e é lá que ele é
 * verificado: assinatura contra a chave pública do Google, emissor, validade e
 * `aud` igual ao nosso client id.
 *
 * A leitura dos claims aqui é **só para escrever o nome na tela**. Qualquer
 * pessoa consegue montar um JWT com o nome que quiser — a parte que não dá para
 * forjar é a assinatura, e conferir assinatura é trabalho de quem guarda os
 * dados, não do navegador. Se esta função virar fonte de decisão de acesso, o
 * sistema já está quebrado.
 */
export function signInWithGoogle(idToken: string): Session {
  const claims = readClaimsForDisplay(idToken);
  session = {
    token: idToken,
    name: claims.name?.trim() || 'Cliente',
    email: claims.email ?? '',
  };
  localStorage.setItem(TOKEN_KEY, JSON.stringify(session));
  return session;
}

function readClaimsForDisplay(idToken: string): { name?: string; email?: string } {
  try {
    const payload = idToken.split('.')[1];
    if (!payload) return {};
    // base64url → base64, com o padding que o JWT omite.
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
    // atob devolve bytes, não texto: sem o TextDecoder, "Solângela" chega torto.
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return {};
  }
}

export function signOut() {
  session = null;
  localStorage.removeItem(TOKEN_KEY);
}

/* ------------------------------------------------------------------ *
 * Cobrança
 *
 * Esta é a única chamada que vai para o Payment Service. Ela existe porque o
 * `client_secret` — o que autoriza confirmar o cartão — nasce lá, e levá-lo até
 * o Order Service exigiria uma chamada HTTP entre serviços, proibida neste
 * projeto, ou mandar a credencial dentro de um evento Kafka.
 *
 * O navegador fala com os três serviços; os três continuam sem se falar.
 * ------------------------------------------------------------------ */

export interface OrderPayment {
  orderId: string;
  method: PaymentMethod;
  status: string;
  /** Ausente quando não há o que confirmar — inclusive antes de a cobrança existir. */
  clientSecret?: string;
}

/**
 * Devolve `null` para 404.
 *
 * <p>404 aqui é esperado e não é erro: quem cria a cobrança é um consumidor de
 * Kafka, então há uma janela entre o pedido ser aceito e a cobrança existir. A
 * tela trata como "ainda não" e pergunta de novo.
 */
export async function getOrderPayment(orderId: string): Promise<OrderPayment | null> {
  try {
    return await request<OrderPayment>(`/api/v1/payments/by-order/${orderId}`);
  } catch (e) {
    if (e instanceof ProblemError && e.status === 404) return null;
    throw e;
  }
}

/**
 * Instância gratuita hiberna, e acordar demora mais que o proxy aguenta.
 *
 * O Render derruba o serviço após ~15 minutos parado, e o cold start leva perto
 * de um minuto — mais que o tempo que o proxy da Vercel espera. O resultado era
 * a primeira visita depois de um tempo ocioso receber um erro seco ("o Order
 * Service está no ar?") quando o serviço estava, sim, subindo.
 *
 * Nada disso é consertável do lado do servidor sem instância paga. O que dá
 * para consertar é a mentira: tentar de novo em vez de desistir na primeira, já
 * que a segunda tentativa pega o serviço acordado.
 *
 * Só para 502/503/504 e falha de rede, e só em GET. Um POST repetido aqui seria
 * outro problema — os que existem carregam Idempotency-Key, mas depender disso
 * numa camada genérica é o tipo de suposição que envelhece mal.
 */
const WAKE_UP_STATUSES = [502, 503, 504];
const WAKE_UP_ATTEMPTS = 3;

function isRetryable(method: string, status: number | null): boolean {
  if (method.toUpperCase() !== 'GET') return false;
  return status === null || WAKE_UP_STATUSES.includes(status);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const active = currentSession();
  const method = init?.method ?? 'GET';
  let response: Response | null = null;

  for (let attempt = 1; attempt <= WAKE_UP_ATTEMPTS; attempt++) {
    try {
      response = await fetch(path, {
        ...init,
        headers: {
          'Content-Type': 'application/json',
          ...(active ? { Authorization: `Bearer ${active.token}` } : {}),
          ...(init?.headers ?? {}),
        },
      });
      if (!isRetryable(method, response.status) || attempt === WAKE_UP_ATTEMPTS) break;
    } catch (networkError) {
      // Sem resposta nenhuma: proxy desistiu ou a rede caiu. A primeira é a
      // comum aqui, e é justamente a que vale repetir.
      if (!isRetryable(method, null) || attempt === WAKE_UP_ATTEMPTS) throw networkError;
    }
    // Espera curta: o serviço já está subindo por causa da tentativa anterior,
    // então o que falta é tempo de boot, não intervalo entre chamadas.
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  if (!response) throw new Error('Sem resposta do servidor');

  if (response.status === 401) {
    // Token expirado ou inválido: derruba a sessão em vez de insistir com ela.
    signOut();
    throw new ProblemError('Sessão expirada', 'Entre novamente para continuar.', 401);
  }

  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new ProblemError(
      problem?.title ?? 'Erro inesperado',
      problem?.detail ?? `HTTP ${response.status}`,
      response.status,
    );
  }
  return response.json() as Promise<T>;
}

/** Só o que os parâmetros do catálogo aceitam de verdade — o resto o front filtra. */
export interface EventFilters {
  city?: string;
  page?: number;
  size?: number;
}

export function listEvents(filters: EventFilters = {}): Promise<Page<EventSummary>> {
  const query = new URLSearchParams();
  // Sempre ON_SALE: o endpoint é público e aceita o status como parâmetro, mas
  // um evento em DRAFT ainda está sendo montado e não é para ser visto.
  query.set('status', 'ON_SALE');
  if (filters.city) query.set('city', filters.city);
  if (filters.page !== undefined) query.set('page', String(filters.page));
  if (filters.size !== undefined) query.set('size', String(filters.size));

  return request<Page<EventSummary>>(`/api/v1/events?${query}`);
}

export function getEvent(eventId: string): Promise<EventDetail> {
  return request<EventDetail>(`/api/v1/events/${eventId}`);
}

export function getOrder(orderId: string): Promise<Order> {
  return request<Order>(`/api/v1/orders/${orderId}`);
}

/**
 * Cancela o pedido e devolve ele já cancelado.
 *
 * `POST` e não `DELETE`: o pedido não deixa de existir, muda de estado — e esse
 * registro é parte do que a tela mostra.
 *
 * O backend responde 409 quando o pedido já acabou (pago, cancelado, expirado).
 * Isso não é erro de quem clicou: é a corrida normal entre o botão e o
 * pagamento chegando. Quem chama trata como "já resolveu sozinho" e mostra o
 * estado atual, sem cara de falha.
 */
export function cancelOrder(orderId: string): Promise<Order> {
  return request<Order>(`/api/v1/orders/${orderId}/cancel`, { method: 'POST' });
}

export function listMyOrders(status?: OrderStatus): Promise<Page<Order>> {
  const query = status ? `?status=${status}` : '';
  return request<Page<Order>>(`/api/v1/orders${query}`);
}

export type PaymentMethod = 'CREDIT_CARD' | 'PIX' | 'BOLETO';

export interface OrderItemInput {
  ticketCategoryId: string;
  quantity: number;
}

export interface PlaceOrderInput {
  eventId: string;
  /** Até 10 categorias no mesmo pedido, como o contrato permite. */
  items: OrderItemInput[];
  paymentMethod: PaymentMethod;
}

export function placeOrder(input: PlaceOrderInput): Promise<Order> {
  return request<Order>('/api/v1/orders', {
    method: 'POST',
    headers: {
      // Gerada no cliente e obrigatória: se a rede cair depois de o servidor ter
      // gravado, o retry devolve o pedido original em vez de cobrar duas vezes.
      'Idempotency-Key': crypto.randomUUID(),
    },
    body: JSON.stringify({
      eventId: input.eventId,
      paymentMethod: input.paymentMethod,
      items: input.items,
    }),
  });
}

/* ------------------------------------------------------------------ *
 * Ingressos — Notification Service
 *
 * Serviço diferente, dono de dados diferentes. O front fala com os dois
 * porque cada um é responsável pelo que guarda; o Order Service não
 * conhece ingresso, e perguntar a ele seria acoplar os dois.
 * ------------------------------------------------------------------ */

export interface Ticket {
  id: string;
  ticketCode: string;
  orderId: string;
  eventName: string | null;
  categoryName: string | null;
  holderName: string;
  qrCodePayload: string;
  status: string;
  issuedAt: string;
}

export function listTickets(orderId?: string): Promise<{ content: Ticket[] }> {
  const query = orderId ? `?orderId=${encodeURIComponent(orderId)}` : '';
  return request<{ content: Ticket[] }>(`/api/v1/tickets${query}`);
}
