/**
 * Acorda os serviços que ninguém chama por HTTP.
 *
 * O Payment e o Notification são consumidores de Kafka. O navegador só fala com
 * eles depois que já existe pedido, e o `keep-awake.yml` cobre sete horas por
 * dia — fora dessa janela os dois hibernam, porque instância gratuita do Render
 * dorme depois de quinze minutos sem requisição.
 *
 * Dormindo, o estrago não aparece: o pedido é aceito, o evento fica no tópico e
 * o pagamento não resolve enquanto ninguém subir o consumidor. A tela fica em
 * "aguardando pagamento" para sempre, sem erro em lugar nenhum. Foi exatamente
 * isso que segurou seis pedidos por meia hora — e os seis andaram sozinhos, nos
 * segundos seguintes ao serviço voltar, porque as mensagens continuavam lá.
 *
 * Abrir o site passa a ser o gatilho: um GET no health de cada um é o que tira o
 * contêiner da hibernação. O boot leva perto de um minuto, e é tempo que corre
 * enquanto a pessoa escolhe o ingresso.
 *
 * Isto não fura a regra de não haver chamada entre serviços: quem chama é o
 * navegador, que já fala com os três.
 */

const ALVOS = ['/wake/payment', '/wake/notification'];

/** A hibernação começa aos quinze minutos; dez deixa margem para um ping falhar. */
const INTERVALO_MS = 10 * 60 * 1000;

function acordar() {
  // Aba escondida não compra. Acordar serviço para ninguém só gasta as horas
  // gratuitas, que são do workspace inteiro e não sobram.
  if (document.hidden) return;

  for (const alvo of ALVOS) {
    // Falha não tem tratamento porque não tem consequência: o ciclo seguinte
    // tenta de novo, e a compra em si já tem os próprios erros de tela.
    fetch(alvo, { cache: 'no-store' }).catch(() => {});
  }
}

export function keepServicesAwake(): () => void {
  acordar();
  const timer = window.setInterval(acordar, INTERVALO_MS);
  document.addEventListener('visibilitychange', acordar);

  return () => {
    window.clearInterval(timer);
    document.removeEventListener('visibilitychange', acordar);
  };
}
