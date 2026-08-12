import { Icon } from './Icon';

const STEPS = ['Ingressos', 'Pagamento', 'Confirmação'];

interface Props {
  /** 1, 2 ou 3. */
  current: number;
}

/**
 * As três etapas da compra. Existe para responder "quanto falta" antes de a
 * pessoa perguntar — e para deixar claro, na etapa 3, que acabou.
 */
export function Stepper({ current }: Props) {
  return (
    <ol className="stepper" aria-label={`Etapa ${current} de ${STEPS.length}`}>
      {STEPS.map((label, index) => {
        const step = index + 1;
        const state = step < current ? 'done' : step === current ? 'current' : '';
        return (
          <li key={label} style={{ display: 'contents' }}>
            {index > 0 && <span className={`step-line${step <= current ? ' done' : ''}`} aria-hidden="true" />}
            <span className={`step ${state}`} aria-current={step === current ? 'step' : undefined}>
              <span className="step-mark">
                {step < current ? <Icon name="check" size={14} /> : step}
              </span>
              {label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
