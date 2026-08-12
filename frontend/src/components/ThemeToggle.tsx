import { useEffect, useState } from 'react';
import { applyPreference, storedPreference, type ThemePreference } from '../lib/theme';
import { Icon, type IconName } from './Icon';

const OPTIONS: { value: ThemePreference; icon: IconName; label: string }[] = [
  { value: 'light', icon: 'sun', label: 'Tema claro' },
  { value: 'system', icon: 'monitor', label: 'Seguir o sistema' },
  { value: 'dark', icon: 'moon', label: 'Tema escuro' },
];

/**
 * Três estados explícitos em vez de um interruptor.
 *
 * Com dois estados não há como voltar para "o que o sistema mandar" depois de
 * ter clicado uma vez — e é justamente o que a maioria das pessoas quer, só que
 * ninguém sabe dizer isso em voz alta.
 */
export function ThemeToggle() {
  const [preference, setPreference] = useState<ThemePreference>(storedPreference);

  useEffect(() => {
    applyPreference(preference);
  }, [preference]);

  return (
    <div className="theme-toggle" role="group" aria-label="Tema">
      {OPTIONS.map((option) => (
        <button
          key={option.value}
          type="button"
          title={option.label}
          aria-label={option.label}
          aria-pressed={preference === option.value}
          onClick={() => setPreference(option.value)}
        >
          <Icon name={option.icon} size={15} />
        </button>
      ))}
    </div>
  );
}
