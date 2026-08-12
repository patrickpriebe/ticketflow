import { useEffect, useState } from 'react';
import { Icon } from './Icon';

interface Props {
  /** Instante ISO em que o pedido expira. */
  deadline: string;
}

function remaining(deadline: string) {
  return Math.max(0, new Date(deadline).getTime() - Date.now());
}

/**
 * Quanto tempo resta até o pedido expirar.
 *
 * O prazo sempre existiu — o job de expiração devolve os ingressos ao estoque
 * quando ele estoura — mas era invisível, e o cliente só descobria perdendo a
 * reserva. Abaixo de dois minutos o relógio muda de cor: nesse ponto a
 * informação deixa de ser contexto e vira aviso.
 */
export function Countdown({ deadline }: Props) {
  const [left, setLeft] = useState(() => remaining(deadline));

  useEffect(() => {
    setLeft(remaining(deadline));
    const timer = setInterval(() => setLeft(remaining(deadline)), 1000);
    return () => clearInterval(timer);
  }, [deadline]);

  if (left === 0) {
    return <span className="countdown urgent">Prazo encerrado</span>;
  }

  const totalSeconds = Math.floor(left / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  return (
    <span className={`countdown${left < 120_000 ? ' urgent' : ''}`}>
      <Icon name="clock" size={16} />
      {String(minutes).padStart(2, '0')}:{String(seconds).padStart(2, '0')}
    </span>
  );
}
