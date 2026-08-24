// Roda antes da primeira pintura: sem isto a página aparece clara e escurece um
// quadro depois, no piscar que todo site com tema escuro tem quando a
// preferência só é lida dentro do React.
//
// Vive num arquivo em vez de inline por causa do Content-Security-Policy. Um
// `script-src` que aceite código inline aceita também o que um XSS injetar, e a
// alternativa — fixar o hash do trecho no cabeçalho — quebra em silêncio a cada
// edição destas linhas: a política passa a recusar o script, o tema volta a
// piscar e nada acusa o motivo.
(function () {
  var root = document.documentElement;

  try {
    var stored = localStorage.getItem('ticketflow.theme');
    if (stored === 'light' || stored === 'dark') {
      root.setAttribute('data-theme', stored);
    }
  } catch (e) {
    /* localStorage bloqueado: o CSS decide pelo prefers-color-scheme */
  }

  // A pele visual pelo mesmo motivo, e com urgência maior: ela troca a fonte e
  // a paleta inteira. Lida só dentro do React, quem escolheu o desenho clássico
  // veria o de bilheteria por um quadro a cada carregamento.
  //
  // O atributo é escrito sempre, inclusive no padrão: assim o CSS pode marcar
  // as duas peles por atributo e nenhuma delas depende da ausência da outra.
  var skin = 'boxoffice';
  try {
    var chosen = localStorage.getItem('ticketflow.skin');
    if (['boxoffice', 'stage', 'poster', 'classic'].indexOf(chosen) !== -1) {
      skin = chosen;
    }
  } catch (e) {
    /* sem armazenamento: vale o padrão */
  }
  root.setAttribute('data-skin', skin);
})();
