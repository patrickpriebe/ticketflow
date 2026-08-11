/**
 * Cliente do Order Service.
 *
 * Os tipos aqui espelham `contracts/openapi/order-service.yaml`. Não são gerados
 * a partir dele — gerar traria um passo de build para um front deste tamanho — mas
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
  items: { categoryName: string; quantity: number; subtotal: Money }[];
  totalAmount: Money;
  statusHistory: OrderStatusChange[];
  createdAt: string;
}

interface Page<T> {
  content: T[];
  page: { totalElements: number };
}

/** RFC 7807. O backend responde `application/problem+json` em todo erro. */
export class ProblemError extends Error {
  constructor(readonly title: string, readonly detail: string, readonly status: number) {
    super(detail || title);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });

  if (!response.ok) {
    // O corpo de problema traz título e detalhe legíveis; usá-los é melhor que
    // inventar uma mensagem genérica na interface.
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

export interface PlaceOrderInput {
  eventId: string;
  ticketCategoryId: string;
  quantity: number;
  customer: { id: string; name: string; email: string };
}

export function placeOrder(input: PlaceOrderInput): Promise<Order> {
  return request<Order>('/api/v1/orders', {
    method: 'POST',
    headers: {
      // Gerada no cliente e obrigatória: se a rede cair depois do servidor ter
      // gravado, o retry devolve o pedido original em vez de cobrar duas vezes.
      'Idempotency-Key': crypto.randomUUID(),
    },
    body: JSON.stringify({
      customer: input.customer,
      eventId: input.eventId,
      paymentMethod: 'CREDIT_CARD',
      items: [{ ticketCategoryId: input.ticketCategoryId, quantity: input.quantity }],
    }),
  });
}
