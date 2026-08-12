import type { EventDetail, Money, OrderItemInput } from '../api';

/**
 * O carrinho, do lado do cliente.
 *
 * Não existe carrinho no backend de propósito: nada é reservado enquanto a
 * pessoa escolhe. A reserva acontece no `POST /orders`, numa transação só, e é
 * lá que o estoque é conferido. Um carrinho servidor teria de segurar assento
 * por tempo indeterminado para todo mundo que abriu a página — é o tipo de
 * coisa que parece cuidado e vira estoque preso.
 *
 * Guardado em `sessionStorage` para que recarregar a página no meio do checkout
 * não jogue a escolha fora. Aba fechou, acabou.
 */

const STORAGE_KEY = 'ticketflow.cart';

/** Teto do contrato: `items` aceita no máximo 10 categorias por pedido. */
export const MAX_CATEGORIES = 10;

export interface CartLine {
  categoryId: string;
  name: string;
  unitPrice: Money;
  quantity: number;
  /** Estoque no momento da escolha; o backend confere de novo na hora de reservar. */
  available: number;
}

export interface Cart {
  eventId: string;
  eventName: string;
  venue: string;
  city: string;
  startsAt: string;
  lines: CartLine[];
}

export function emptyCart(event: EventDetail): Cart {
  return {
    eventId: event.id,
    eventName: event.name,
    venue: event.venue,
    city: event.city,
    startsAt: event.startsAt,
    lines: [],
  };
}

export function setQuantity(cart: Cart, line: Omit<CartLine, 'quantity'>, quantity: number): Cart {
  const others = cart.lines.filter((existing) => existing.categoryId !== line.categoryId);
  const lines = quantity > 0 ? [...others, { ...line, quantity }] : others;

  // Ordem estável: sem isto a linha pula para o fim da lista toda vez que a
  // quantidade vai a zero e volta.
  return { ...cart, lines: lines.sort((a, b) => a.name.localeCompare(b.name)) };
}

export function quantityOf(cart: Cart, categoryId: string): number {
  return cart.lines.find((line) => line.categoryId === categoryId)?.quantity ?? 0;
}

export function cartCount(cart: Cart): number {
  return cart.lines.reduce((sum, line) => sum + line.quantity, 0);
}

export function cartTotal(cart: Cart): Money {
  const amount = cart.lines.reduce((sum, line) => sum + line.unitPrice.amount * line.quantity, 0);
  return { amount, currency: cart.lines[0]?.unitPrice.currency ?? 'BRL' };
}

export function toOrderItems(cart: Cart): OrderItemInput[] {
  return cart.lines.map((line) => ({ ticketCategoryId: line.categoryId, quantity: line.quantity }));
}

export function loadCart(): Cart | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Cart;
  } catch {
    sessionStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function saveCart(cart: Cart | null) {
  if (cart && cart.lines.length > 0) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(cart));
  else sessionStorage.removeItem(STORAGE_KEY);
}
