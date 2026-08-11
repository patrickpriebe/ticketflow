# Eventos Kafka

Schemas formais em [`contracts/events/`](../contracts/events/).

## Tópicos

| Tópico | Partições | Retenção | Produz | Consome |
|---|---|---|---|---|
| `ticketflow.orders.created` | 3 | 7 dias | order-service | payment-service |
| `ticketflow.payments.processed` | 3 | 7 dias | payment-service | order-service, notification-service |
| `ticketflow.orders.created.dlq` | 1 | 30 dias | payment-service | ninguém (inspeção manual) |
| `ticketflow.payments.processed.dlq` | 1 | 30 dias | consumidores | ninguém (inspeção manual) |

**Chave da mensagem: sempre o `orderId`.** Todos os eventos de um pedido caem na
mesma partição e ficam ordenados entre si. Sem isso, `PAGAMENTO_APROVADO` poderia
ser processado antes do `ORDER_CREATED` do mesmo pedido.

**3 partições.** Permite até 3 instâncias de um consumer group processando em
paralelo. Aumentar partições depois é fácil; diminuir, não — então começa em 3, que
já demonstra o paralelismo sem inflar o ambiente local.

**DLQ com 1 partição e retenção longa.** Ordem não importa numa DLQ; o que importa
é a mensagem não sumir antes de alguém olhar.

**`auto.create.topics.enable=false`.** Um erro de digitação no nome do tópico tem
que estourar, não criar um tópico fantasma que ninguém lê. Os tópicos nascem em
[`infra/kafka/create-topics.sh`](../infra/kafka/create-topics.sh).

## Envelope

Toda mensagem, em todo tópico, usa o mesmo envelope — assim qualquer consumidor
consegue deduplicar e rastrear sem entender o payload:

```json
{
  "eventId": "6f1a2b3c-4d5e-4f60-8a1b-2c3d4e5f6a7b",
  "eventType": "ORDER_CREATED",
  "eventVersion": 1,
  "occurredAt": "2026-08-10T14:03:21Z",
  "producer": "order-service",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "correlationId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "data": { }
}
```

| Campo | Para que serve |
|---|---|
| `eventId` | chave de deduplicação — é o que vai para `processed_events` |
| `eventType` | discriminador; determina o formato de `data` |
| `eventVersion` | versão do payload; sobe em mudança quebra-contrato |
| `occurredAt` | quando o fato aconteceu, não quando foi publicado |
| `traceId` | trace do OpenTelemetry, propagado ponta a ponta (fase 3) |
| `correlationId` | normalmente o `orderId`; facilita ler mensagem crua no Kafka UI |

Atenção a uma armadilha de nome: `envelope.eventId` é o **id da mensagem**;
`data.eventId`, no `ORDER_CREATED`, é o **id do show**. São coisas diferentes.

## Os três eventos

### `ORDER_CREATED`

`order-service` → `ticketflow.orders.created`, gravado no outbox na mesma transação
do pedido. Carrega tudo que o Payment Service precisa para cobrar.

**Não carrega dado de cartão.** As credenciais de pagamento vão do cliente direto ao
gateway; nunca trafegam pelo Kafka nem ficam em log.

### `PAGAMENTO_APROVADO` / `PAGAMENTO_RECUSADO`

`payment-service` → `ticketflow.payments.processed`, depois da resposta do gateway.

Os dois compartilham um único tópico. Separá-los em dois tópicos perderia a garantia
de ordem entre eles, e quem se interessa por um quase sempre se interessa pelo
outro — o `eventType` já distingue.

`PAGAMENTO_APROVADO` obriga `gatewayTransactionId`; `PAGAMENTO_RECUSADO` obriga
`failureCode`. Isso está no JSON Schema, não só na convenção.

> Estes dois nomes estão em português por serem vocabulário fixo do projeto. Todo o
> resto do código é em inglês. É a única exceção, e é deliberada.

## Retry e DLQ

Regra em qualquer consumidor:

1. **Erro transitório** (gateway fora, banco indisponível) → retry com backoff
   exponencial.
2. **Erro permanente** (payload inválido, evento para pedido inexistente) → DLQ
   direto. Retentar não vai consertar.
3. **Estouro das tentativas** → DLQ, com o erro nos headers.

Nunca engolir a exceção e commitar o offset: a mensagem some sem ninguém saber.

## Compatibilidade de schema

Regra ao evoluir um payload:

- Campo novo **opcional** → compatível, não sobe `eventVersion`.
- Campo obrigatório novo, campo removido ou tipo alterado → **quebra**. Sobe
  `eventVersion`, e o consumidor lê as duas versões até o produtor parar de emitir
  a antiga.

Nunca reaproveitar um nome de campo com outro significado — é o tipo de mudança que
passa em todos os testes e quebra em produção.
