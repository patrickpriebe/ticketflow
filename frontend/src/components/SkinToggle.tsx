import { useId } from 'react';
import { applySkin, SKINS, useSkin, type Skin } from '../lib/skin';

/**
 * Escolha entre os desenhos do site.
 *
 * Aparece duas vezes: no cabeçalho, quando há largura de sobra, e no rodapé,
 * que é onde uma preferência costuma morar. Só no rodapé não bastava — uma
 * escolha que exige rolar a página inteira até o fim é uma escolha que ninguém
 * encontra.
 *
 * É um `<select>` e não uma fileira de botões. Com dois desenhos os botões
 * cabiam; com quatro eles pedem uns 380px, que o cabeçalho não tem. O select
 * nativo ocupa a largura de um item, já vem com teclado e leitor de tela
 * prontos, e no telefone abre a roleta do próprio sistema.
 *
 * O estado não mora aqui dentro. Com um `useState` por instância, clicar no
 * controle do cabeçalho trocava a pele do site inteiro e o do rodapé continuava
 * marcando a escolha anterior. `useSkin` lê o atributo do <html>, que é a mesma
 * fonte que o CSS usa, então as duas cópias não têm como divergir — nem entre
 * si, nem da tela.
 */
export function SkinToggle() {
  const skin = useSkin();

  // Com identificador fixo, as duas instâncias emitiriam o mesmo `id` e o
  // <label> apontaria para o primeiro: clicar no rótulo do rodapé colocaria o
  // foco no select do cabeçalho.
  const id = useId();

  return (
    <div className="skin-toggle">
      {/* O rótulo some da tela e continua no leitor: um <select> sem nome
          acessível é lido como "caixa de combinação" e mais nada. O que ele
          escolhe já se descobre abrindo — o campo fechado mostra o nome do
          desenho em vigor, que é a resposta que a palavra dava. */}
      <label className="sr-only" htmlFor={id}>
        Desenho
      </label>

      {/* A opção carrega só o nome. O campo fechado mostra o texto da opção
          escolhida, então juntar nome e descrição ali deixava "Bilh…" no
          cabeçalho. A descrição de cada desenho vira legenda de leitor de
          tela, fora do texto que precisa caber na linha. */}
      <select
        id={id}
        className="skin-select"
        value={skin}
        aria-describedby={`${id}-hint`}
        onChange={(event) => applySkin(event.target.value as Skin)}
      >
        {SKINS.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>

      <span className="sr-only" id={`${id}-hint`}>
        {SKINS.map((option) => `${option.label}: ${option.hint}.`).join(' ')}
      </span>
    </div>
  );
}
