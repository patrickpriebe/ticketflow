import { useEffect, useState } from 'react';

interface Props {
  deadline: string;
}

/**
 * Quanto falta para o pedido expirar.
 *
 * O backend guarda o prazo e um job devolve os ingressos ao estoque quando ele
 * vence. Sem mostrar isso, um pedido aguardando pagamento parece que espera para
 * sempre — e o cliente descobre o relógio só quando perde a reserva.
 */
export function Countdown({ deadline }: Props) {
  const [remaining, setRemaining] = useState(() => msUntil(deadline));

  useEffect(() => {
    setRemaining(msUntil(deadline));
    const timer = window.setInterval(() => setRemaining(msUntil(deadline)), 1000);
    return () => window.clearInterval(timer);
  }, [deadline]);

  if (remaining <= 0) {
    return <span className="countdown late">prazo encerrado</span>;
  }

  const totalSeconds = Math.floor(remaining / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  // Abaixo de dois minutos vira alerta: é quando ainda dá tempo de agir.
  const urgent = totalSeconds < 120;

  return (
    <span className={`countdown${urgent ? ' urgent' : ''}`}>
      {minutes}:{String(seconds).padStart(2, '0')} para expirar
    </span>
  );
}

function msUntil(deadline: string): number {
  return new Date(deadline).getTime() - Date.now();
}
