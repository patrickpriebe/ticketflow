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
