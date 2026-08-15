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
  try {
    var stored = localStorage.getItem('ticketflow.theme');
    if (stored === 'light' || stored === 'dark') {
      document.documentElement.setAttribute('data-theme', stored);
    }
  } catch (e) {
    /* localStorage bloqueado: o CSS decide pelo prefers-color-scheme */
  }
})();
