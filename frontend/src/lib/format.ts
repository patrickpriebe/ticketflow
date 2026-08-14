const MONTHS = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ'];

export const money = (value: { amount: number; currency: string }) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: value.currency }).format(value.amount);

export const dateTime = (iso: string) =>
  new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(iso));

export const time = (iso: string) =>
  new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' }).format(new Date(iso));

/** "sexta-feira, 18 de setembro" — para o cabeçalho do evento. */
export const longDate = (iso: string) =>
  new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date(iso));

/** Dia e mês curtos, para o selo de calendário sobre a arte do evento. */
export function calendarBadge(iso: string) {
  const date = new Date(iso);
  return { day: String(date.getDate()).padStart(2, '0'), month: MONTHS[date.getMonth()] };
}

/** Quantos dias faltam. Negativo é passado; usado só para rótulos de urgência. */
export function daysUntil(iso: string): number {
  const diff = new Date(iso).getTime() - Date.now();
  return Math.ceil(diff / 86_400_000);
}
