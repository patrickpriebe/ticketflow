/**
 * A foto de cada evento do catálogo de demonstração.
 *
 * São fotografias reais **dos locais que o catálogo cita** — Parque Olímpico,
 * Theatro Municipal, Sesc Pompeia, Marina da Glória —, todas do Wikimedia
 * Commons, com licença livre e autor creditado em `CREDITS.json` ao lado dos
 * arquivos.
 *
 * A distinção importa e é deliberada: os eventos são fictícios, então não
 * existe foto "daquele show". O que existe é o lugar, e ele é de verdade. Usar
 * imagem de um artista real para ilustrar um evento inventado passaria de
 * enfeite a afirmação falsa.
 *
 * <p><strong>Nenhuma delas tem pessoa identificável</strong>, e isso é critério
 * de seleção, não sorte. A licença CC resolve o direito do fotógrafo; o direito
 * de imagem de quem está na foto é outro, pertence a cada pessoa retratada e
 * não se resolve com crédito. Um local vazio não tem essa segunda camada. Onde
 * só existia foto com público, o evento fica sem foto.
 *
 * Servidas pelo próprio site, e não linkadas de fora, por dois motivos: a CSP
 * fecha `img-src` em `'self'`, e um card sem imagem porque um terceiro saiu do
 * ar é uma falha que não precisa existir.
 */
export interface EventPhoto {
  src: string;
  /** Onde a foto foi tirada — o que a legenda mostra. */
  venue: string;
  author: string;
  license: string;
  source: string;
}

const PHOTOS: Record<string, EventPhoto> = {
  '11111111-1111-4111-8111-111111111111': {
    src: '/img/events/parque-olimpico.jpg',
    venue: 'Parque Olímpico, Rio de Janeiro',
    author: 'Miriam Jeske/Brasil2016.gov.br',
    license: 'CC BY 3.0 br',
    source: 'https://commons.wikimedia.org/wiki/File:Parque_Ol%C3%ADmpico_Rio_2016.jpg',
  },
  '22222222-2222-4222-8222-222222222222': {
    src: '/img/events/theatro-municipal.jpg',
    venue: 'Theatro Municipal, São Paulo',
    author: 'Wilfredor',
    license: 'CC0',
    source: 'https://commons.wikimedia.org/wiki/File:Interior_Teatro_Municipal_de_S%C3%A3o_Paulo.jpg',
  },
  '33333333-3333-4333-8333-333333333333': {
    src: '/img/events/mineirao.jpg',
    venue: 'Mineirão, Belo Horizonte',
    author: 'Rodrigo Lima/Portal da Copa',
    license: 'CC BY 2.0',
    source: 'https://commons.wikimedia.org/wiki/File:Mineir%C3%A3o_A%C3%A9rea.jpg',
  },
  '44444444-4444-4444-8444-444444444444': {
    src: '/img/events/sesc-pompeia.jpg',
    venue: 'Sesc Pompeia, São Paulo',
    author: 'Clarissa Sá',
    license: 'CC BY-SA 4.0',
    source: 'https://commons.wikimedia.org/wiki/File:Sesc_Pompeia.jpg',
  },
  // O Teatro Rival não tem entrada aqui de propósito. As fotos dele no Commons
  // são todas de apresentação, com dezenas de rostos identificáveis — e a
  // licença CC cobre o direito do fotógrafo, não o direito de imagem de quem
  // aparece. São coisas diferentes, e a segunda não se resolve creditando.
  // Este evento cai no pôster desenhado, que é exatamente para isto que existe.
  '66666666-6666-4666-8666-666666666666': {
    src: '/img/events/arena-gremio.jpg',
    venue: 'Arena do Grêmio, Porto Alegre',
    author: 'Guivargas1',
    license: 'CC0',
    source: 'https://commons.wikimedia.org/wiki/File:Arena_do_Gr%C3%AAmio_-_Lado_de_fora.jpg',
  },
  '77777777-7777-4777-8777-777777777777': {
    src: '/img/events/marina-da-gloria.jpg',
    venue: 'Marina da Glória, Rio de Janeiro',
    author: 'Wusel007',
    license: 'CC BY 4.0',
    source: 'https://commons.wikimedia.org/wiki/File:Rio_de_Janeiro_Marina_da_Gloria.jpg',
  },
  '88888888-8888-4888-8888-888888888888': {
    src: '/img/events/pedreira-leminski.jpg',
    venue: 'Pedreira Paulo Leminski, Curitiba',
    author: 'Gustavo L. Simianer Procat',
    license: 'CC BY 2.0',
    source: 'https://commons.wikimedia.org/wiki/File:Pedreira_Paulo_Leminski_-_Curitiba_PR_(5517408663).jpg',
  },
  '99999999-9999-4999-8999-999999999999': {
    src: '/img/events/teatro-castro-alves.jpg',
    venue: 'Teatro Castro Alves, Salvador',
    author: 'Paul R. Burley',
    license: 'CC BY-SA 4.0',
    source: 'https://commons.wikimedia.org/wiki/File:Teatro_Castro_Alves_Salvador_Bahia_2021-8213.jpg',
  },
};

/**
 * A foto daquele evento, ou `null`.
 *
 * `null` é caso normal, não erro: um evento cadastrado depois do seed não tem
 * foto aqui, e o pôster desenhado continua sendo o que aparece. É por isso que
 * a arte generativa não foi removida — ela deixou de ser a única opção e virou
 * o fallback.
 */
export function photoFor(eventId: string): EventPhoto | null {
  return PHOTOS[eventId] ?? null;
}
