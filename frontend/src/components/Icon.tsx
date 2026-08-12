/**
 * Conjunto de ícones em traço, desenhados na mesma grade de 24 e com a mesma
 * espessura. Inline em vez de uma fonte de ícones ou de um pacote: são
 * dezoito símbolos, herdam `currentColor` de graça e não custam requisição
 * nenhuma nem um flash de ícone faltando antes da fonte carregar.
 */

export type IconName =
  | 'search'
  | 'pin'
  | 'calendar'
  | 'clock'
  | 'sun'
  | 'moon'
  | 'monitor'
  | 'ticket'
  | 'check'
  | 'chevron'
  | 'arrow-left'
  | 'user'
  | 'shield'
  | 'bolt'
  | 'card'
  | 'pix'
  | 'barcode'
  | 'close'
  | 'inbox';

const PATHS: Record<IconName, React.ReactNode> = {
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </>
  ),
  pin: (
    <>
      <path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" />
      <circle cx="12" cy="10" r="3" />
    </>
  ),
  calendar: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M3 10h18M8 3v4M16 3v4" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ),
  sun: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </>
  ),
  moon: <path d="M21 13A9 9 0 1 1 11 3a7 7 0 0 0 10 10Z" />,
  monitor: (
    <>
      <rect x="3" y="4" width="18" height="12" rx="2" />
      <path d="M8 20h8M12 16v4" />
    </>
  ),
  ticket: (
    <>
      <path d="M3 9V7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 6v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-6Z" />
      <path d="M14 5v14" strokeDasharray="2 3" />
    </>
  ),
  check: <path d="m5 13 4 4L19 7" />,
  chevron: <path d="m9 6 6 6-6 6" />,
  'arrow-left': <path d="M19 12H5m6-7-7 7 7 7" />,
  user: (
    <>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21a8 8 0 0 1 16 0" />
    </>
  ),
  shield: <path d="M12 3l8 3v6c0 5-3.4 8.4-8 9-4.6-.6-8-4-8-9V6Z" />,
  bolt: <path d="M13 2 4 14h7l-1 8 9-12h-7Z" />,
  card: (
    <>
      <rect x="2" y="5" width="20" height="14" rx="2" />
      <path d="M2 10h20M6 15h4" />
    </>
  ),
  // PIX: o losango com os quatro braços, reduzido ao essencial.
  pix: (
    <>
      <path d="M12 3.5 20.5 12 12 20.5 3.5 12Z" />
      <path d="M8.5 8.5 12 12l3.5-3.5M8.5 15.5 12 12l3.5 3.5" />
    </>
  ),
  barcode: (
    <>
      <path d="M3 5v14M7 5v14M11 5v10M15 5v14M19 5v10" />
    </>
  ),
  close: <path d="m6 6 12 12M18 6 6 18" />,
  inbox: (
    <>
      <path d="M3 13h5l1.5 3h5L16 13h5" />
      <path d="M5.5 5h13l2.5 8v6H3v-6Z" />
    </>
  ),
};

interface Props {
  name: IconName;
  size?: number;
  className?: string;
}

export function Icon({ name, size = 16, className }: Props) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      style={{ flexShrink: 0 }}
    >
      {PATHS[name]}
    </svg>
  );
}
