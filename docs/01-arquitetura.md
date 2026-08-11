# Arquitetura

## O problema que o desenho resolve

Vender ingresso tem um pico brutal: quando abre a venda de um show grande, milhares
de pessoas apertam "comprar" no mesmo minuto. Se a API de pedido ficar esperando o
gateway de pagamento responder, cada requisição segura uma thread por segundos e o
sistema cai justamente na hora que importa.

O TicketFlow parte de outra premissa: **o Order Service nunca espera o pagamento**.
Ele grava o pedido como `PENDING`, publica um evento e responde `202 Accepted` em
milissegundos. O pagamento acontece depois, em outro processo, no ritmo dele.

Essa é a decisão central do projeto. Se em algum momento aparecer uma chamada HTTP
síncrona entre os três serviços, a premissa quebrou.

## Os três serviços

```
                    ┌──────────────────────┐
   POST /orders     │                      │   ticketflow.orders.created
  ───────────────►  │    Order Service     ├──────────────────────────┐
   202 Accepted     │                      │   (key = orderId)        │
  ◄───────────────  │  PostgreSQL          │                          │
                    │  ticketflow_orders   │                          ▼
                    │                      │             ┌──────────────────────┐
                    └──────────▲───────────┘             │                      │
                               │                         │   Payment Service    │
                               │                         │                      │
                               │                         │  PostgreSQL          │
                               │                         │  ticketflow_payments │
                               │                         └──────────┬───────────┘
                               │                                    │
                               │                                    │ HTTP
                               │                                    ▼
                               │                         ┌──────────────────────┐
                               │                         │  Gateway externo     │
                               │                         │  (Wiremock em teste) │
                               │                         └──────────────────────┘
                               │
                               │   ticketflow.payments.processed
                               └────────────┬───────────────────────┐
                                            │                       │
                                    (consumer group                 │
                                     order-service)                 ▼
                                                         ┌──────────────────────┐
                                                         │ Notification Service │
                                                         │                      │
                                                         │  MongoDB             │
                                                         │  ticketflow_notif... │
                                                         └──────────────────────┘
```

### Order Service — API REST

Dono do catálogo e dos pedidos. Faz duas coisas:

- **Produz** `ORDER_CREATED` quando um pedido entra.
- **Consome** `PAGAMENTO_APROVADO` / `PAGAMENTO_RECUSADO` para mover o pedido de
  `PENDING` para `PAID` ou `REJECTED`.

Consumir o próprio resultado é o que permite o `GET /orders/{id}` mostrar o status
final sem nunca ter perguntado nada ao Payment Service.

### Payment Service — worker

Não tem API pública. Consome `ORDER_CREATED`, chama o gateway externo por HTTP e
publica o resultado. É o único serviço que fala com o mundo lá fora, e é por isso
que ele é o alvo dos testes de integração com Wiremock (sucesso, recusa, timeout,
5xx — não só o caminho feliz).

### Notification Service

Consome `PAGAMENTO_APROVADO` e emite o ingresso; consome `PAGAMENTO_RECUSADO` e
registra o aviso de falha. Grava tudo no MongoDB.

## Por que Kafka e não uma fila comum

Dois consumidores diferentes precisam do mesmo evento de pagamento (Order Service
para atualizar o status, Notification Service para emitir o ingresso), cada um no
seu ritmo e sem saber da existência do outro. Com consumer groups distintos os dois
leem o mesmo tópico independentemente — acrescentar um quarto serviço amanhã não
exige tocar em quem publica.

A chave da mensagem é sempre o `orderId`. Isso mantém todos os eventos de um mesmo
pedido na mesma partição e, portanto, estritamente ordenados: o `PAGAMENTO_APROVADO`
jamais chega antes do `ORDER_CREATED` daquele pedido.

## Entrega pelo menos uma vez, e o que isso obriga

Kafka entrega *at-least-once*. Uma mensagem pode chegar duas vezes (rebalance,
falha antes do commit do offset). Duas defesas em todo consumidor:

1. **Tabela/coleção `processed_events`** — guarda o `eventId` já processado por
   consumer group. Evento repetido é descartado.
2. **Restrições no banco** — `payments.order_id` é `UNIQUE`, `tickets.ticketCode`
   é único. Se a lógica falhar, o banco não deixa cobrar duas vezes.

## Outbox transacional

Salvar o pedido no Postgres e publicar no Kafka são duas operações em sistemas
diferentes. Se a segunda falhar, o pedido existe e ninguém nunca vai cobrá-lo.

A solução aqui é a tabela `outbox_messages`: o `INSERT` do pedido e o `INSERT` do
evento acontecem na **mesma transação**. Um relay lê as linhas `PENDING` e publica
no Kafka depois. Ou os dois existem, ou nenhum dos dois.

O preço é que a publicação fica assíncrona e pode duplicar (o relay publica, cai
antes de marcar `PUBLISHED`, republica) — o que está tudo bem, porque os
consumidores já são idempotentes pelo item anterior.

## Dois bancos, de propósito

| | PostgreSQL | MongoDB |
|---|---|---|
| Serviços | Order, Payment | Notification |
| Guarda | pedidos, pagamentos, catálogo | ingressos, histórico de notificação |
| Por quê | dado transacional, relacional, com invariantes que precisam de constraint e transação | documento variável — cada canal (e-mail, SMS, push) tem um formato, e o ingresso carrega um *snapshot* do evento |

Order Service e Payment Service compartilham o *container* de Postgres por
conveniência local, mas cada um tem seu **database próprio**. Nenhum enxerga a
tabela do outro — é isso que força a conversa a passar pelo Kafka.

Regra prática: entidade nova claramente transacional vai para o Postgres, mesmo
que haja código Mongo por perto.
