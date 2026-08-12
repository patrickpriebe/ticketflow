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

interface Page<T> {
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
  customerId: string;
  name: string;
  email: string;
}

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

export function signOut() {
  session = null;
  localStorage.removeItem(TOKEN_KEY);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const active = currentSession();
  const response = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(active ? { Authorization: `Bearer ${active.token}` } : {}),
      ...(init?.headers ?? {}),
    },
  });

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

export function listEvents(): Promise<Page<EventSummary>> {
  return request<Page<EventSummary>>('/api/v1/events?status=ON_SALE');
}

export function getEvent(eventId: string): Promise<EventDetail> {
  return request<EventDetail>(`/api/v1/events/${eventId}`);
}

export function getOrder(orderId: string): Promise<Order> {
  return request<Order>(`/api/v1/orders/${orderId}`);
}

export function listMyOrders(): Promise<Page<Order>> {
  return request<Page<Order>>('/api/v1/orders');
}

export type PaymentMethod = 'CREDIT_CARD' | 'PIX' | 'BOLETO';

export interface PlaceOrderInput {
  eventId: string;
  ticketCategoryId: string;
  quantity: number;
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
      items: [{ ticketCategoryId: input.ticketCategoryId, quantity: input.quantity }],
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
