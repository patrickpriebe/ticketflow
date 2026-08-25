# Gateway de pagamento simulado

O Payment Service nunca chama um provedor de verdade — nem em teste, nem no ambiente
local. Este Wiremock faz o papel dele.

Não é preguiça: é o que torna os caminhos infelizes reproduzíveis. Ninguém consegue
pedir a um provedor real que dê timeout sob demanda.

## Regras ativas

| Endpoint | Condição | Resposta |
|---|---|---|
| `POST /v1/charges` | `amount > 2000` | **402** `declined` / `INSUFFICIENT_FUNDS` |
| `POST /v1/charges` | qualquer outro | **201** `approved`, com `transactionId` gerado |
| `POST /v1/pix/charges` | — | **201** `approved` |
| `POST /v1/boletos` | — | **201** `approved` |
| `POST /refunds` | — | **200** `refunded`, com `refundId` gerado |

O estorno tem regra própria porque ele existe: sem esta stub o gateway respondia 404, o
Payment Service lia 4xx como **recusa definitiva** do provedor e a cobrança de um pedido
cancelado ficava `APPROVED` para sempre, sem estornar e sem erro em lugar nenhum. A
distinção que o código faz — 4xx é recusa, 5xx e timeout são indisponibilidade — só vale
se o endpoint existir.

O corte em 2000 foi escolhido para casar com o catálogo de demonstração: um ingresso
**Camarote** custa 2400 e é recusado, enquanto **Pista** custa 650 e é aprovado.
Assim os dois ramos do fluxo podem ser demonstrados sem editar nada.

## Inspecionando

A admin API fica em http://localhost:8090/__admin — útil para ver exatamente o que o
Payment Service enviou, incluindo o header `Idempotency-Key`:

```bash
curl http://localhost:8090/__admin/requests
```

## Simulando timeout e 5xx

Os quatro cenários que o roadmap exige já estão cobertos automaticamente em
`HttpPaymentGatewayTest`, que sobe o Wiremock em processo. Para reproduzir à mão aqui,
adicione um stub com `"fixedDelayMilliseconds"` maior que o `read-timeout` do serviço
(5s), ou com `"status": 503`.

## Este contêiner também roda no ambiente publicado

Ele começou como peça de desenvolvimento, e por um tempo o ambiente publicado
usava o Stripe de verdade. Não deu certo para uma demonstração: nesta conta o
Stripe não tem PIX habilitado, e cartão e boleto só fecham quando alguém confirma
no navegador. O resultado é que **nenhuma compra chegava a "pago"** lá — o pedido
ficava aguardando pagamento até o job de expiração devolver os ingressos.

Então o simulado subiu junto. O código do Stripe continua escrito, testado e a uma
variável de distância: `TICKETFLOW_GATEWAY_PROVIDER` escolhe qual das duas
estratégias atende, e o caso de uso não sabe a diferença — que é exatamente o que
a porta `PaymentGateway` existe para permitir.

O [`Dockerfile`](Dockerfile) assa as regras na imagem, porque hospedagem não monta
volume do repositório. O compose constrói o **mesmo** arquivo e ainda monta
`mappings/` por cima, para continuar valendo a pena editar um stub sem
reconstruir nada.

### A API de administração é trancada por padrão

O [`entrypoint.sh`](entrypoint.sh) decide, e a regra é a que o resto do projeto já
aprendeu doendo: **variável ausente significa trancado**. Sem `GATEWAY_ADMIN_AUTH`
ele sorteia uma senha no boot e ninguém entra.

Abrir exige um "sim" explícito, `GATEWAY_ADMIN_OPEN=true`, e é o que o compose
passa. Publicada aberta, a API de administração deixa qualquer pessoa cadastrar
uma regra nova e escolher o desfecho de toda cobrança do ambiente — o mesmo tipo
de porta que `dev-tokens` e `/actuator/prometheus` já custaram a este projeto.

### Prazos maiores no perfil de nuvem

`connect-timeout` e `read-timeout` sobem para 10s e 45s em `cloud`, e isso não é
afrouxamento: é outro problema. Os cinco segundos do perfil base existem para que
um trabalhador de pagamento não fique pendurado num provedor morto. Aqui o
provedor não está morto — está hibernando, porque instância gratuita dorme depois
de quinze minutos ociosa. Com os prazos curtos, a primeira compra depois de um
período parado esgotava as três tentativas em menos de dez segundos e ia para a
fila de mensagens mortas.
