# Modelagem de dados

Arquivos de origem (a fonte da verdade é sempre a migration, não este documento):

| Onde | O quê |
|---|---|
| [`services/order-service/.../V1__init_order_schema.sql`](../services/order-service/src/main/resources/db/migration/V1__init_order_schema.sql) | schema do `ticketflow_orders` |
| [`services/payment-service/.../V1__init_payment_schema.sql`](../services/payment-service/src/main/resources/db/migration/V1__init_payment_schema.sql) | schema do `ticketflow_payments` |
| [`services/order-service/.../R__seed_demo_catalogue.sql`](../services/order-service/src/main/resources/db/seed/R__seed_demo_catalogue.sql) | catálogo de demonstração (só em dev) |
| [`infra/mongo/init/01-init-notification-db.js`](../infra/mongo/init/01-init-notification-db.js) | coleções do `ticketflow_notifications` |

---

## PostgreSQL — `ticketflow_orders`

```
events ──1:N──► ticket_categories ◄──N:1── order_items ──N:1──► orders ──1:N──► order_status_history
                                                                   │
                                                                   └── (mesma transação) ──► outbox_messages
```

| Tabela | Papel |
|---|---|
| `events` | show, jogo, espetáculo — o que se vende |
| `ticket_categories` | faixas de preço do evento (Pista, VIP…) e o estoque de cada uma |
| `orders` | o pedido; nasce `PENDING` |
| `order_items` | uma linha por categoria comprada |
| `order_status_history` | trilha append-only das transições de status |
| `outbox_messages` | eventos a publicar no Kafka |
| `processed_events` | inbox de idempotência (o serviço também consome) |

### Decisões que valem explicar numa entrevista

**Preço copiado em `order_items.unit_price`.** Um pedido de ontem tem que continuar
mostrando o preço de ontem. Se o valor viesse de um `JOIN` com `ticket_categories`,
um reajuste de preço reescreveria o histórico de todo mundo.

**`orders.idempotency_key UNIQUE`.** O cliente manda o header `Idempotency-Key`. Se
a rede cair depois do servidor ter gravado, o retry bate na constraint e devolve o
pedido original em vez de criar um segundo. Sem isso, timeout de rede vira cobrança
dupla.

**`version BIGINT` em `orders` e `ticket_categories`.** Optimistic locking do JPA.
Duas compras simultâneas do último ingresso: uma vence, a outra leva
`OptimisticLockException` e é rejeitada — em vez de as duas venderem o mesmo lugar.

**`CHECK (reserved_quantity + sold_quantity <= total_quantity)`.** A regra de não
vender mais do que existe fica no banco, não só no código Java. Bug na aplicação
não consegue gravar estado inválido.

**Índice parcial `ix_outbox_dispatchable`.** O relay só olha linhas `PENDING`. Um
índice sobre a tabela inteira cresceria para sempre; o parcial só indexa o que a
consulta usa, e encolhe conforme as mensagens são publicadas.

**Dinheiro em `NUMERIC(12,2)`, nunca `float`.** `0.1 + 0.2` em ponto flutuante não
dá `0.3`, e em valor financeiro isso é defeito. Mapeia para `BigDecimal` no Java.

**Timestamps em `TIMESTAMPTZ`.** Guarda em UTC e converte na borda. Show às 21h em
São Paulo e um servidor em outro fuso não podem discordar sobre quando a venda fecha.

### Ciclo de vida do pedido

```
                 ┌──────── PAGAMENTO_APROVADO ───────► PAID
   POST /orders  │
  ─────────────► PENDING ── PAGAMENTO_RECUSADO ──────► REJECTED
                 │
                 ├── cliente cancela ────────────────► CANCELLED
                 └── expirou sem pagamento ──────────► EXPIRED
```

Só a seta de entrada em `PENDING` é síncrona. Todas as outras chegam por Kafka ou
por um job de expiração.

---

## PostgreSQL — `ticketflow_payments`

| Tabela | Papel |
|---|---|
| `payments` | um pagamento por pedido |
| `payment_attempts` | uma linha por chamada ao gateway externo |
| `processed_events` | inbox de idempotência |
| `outbox_messages` | resultados a publicar |

**`payments.order_id UNIQUE`, sem foreign key.** O `UNIQUE` é a última barreira
contra cobrar duas vezes o mesmo pedido. A ausência de FK é intencional: a tabela
`orders` vive em outro database, e é isso que impede um `JOIN` entre serviços.

**`payment_attempts` existe por causa dos testes.** É ela que torna os cenários do
Wiremock verificáveis: um teste de timeout precisa provar que ficou registrada uma
tentativa com `outcome = 'TIMEOUT'`, não só que o método lançou exceção.

**`REJECTED` ≠ `FAILED`.** `REJECTED` é o gateway dizendo "não" (cartão sem
limite) — resposta final, o cliente precisa ser avisado. `FAILED` é o gateway não
tendo respondido nada de útil (timeout, 5xx) — candidato a retry. Colapsar os dois
num status só apaga a informação de que o retry faz sentido.

**Nada de dado de cartão no banco.** `payment_attempts.request_payload` guarda
requisição mascarada: bandeira e últimos 4 dígitos, nunca PAN, CVV ou validade.

---

## MongoDB — `ticketflow_notifications`

| Coleção | Papel |
|---|---|
| `tickets` | ingresso emitido após pagamento aprovado |
| `notifications` | log de entrega (e-mail hoje; SMS/push depois) |
| `processed_events` | inbox de idempotência, com TTL de 30 dias |

**`eventSnapshot` dentro do ticket.** O ingresso guarda cópia do nome, local e data
do evento. Se o organizador mudar o nome do show depois, o ingresso já emitido
continua legível — e a leitura não precisa de nenhum acesso ao Order Service.
É exatamente o tipo de desnormalização que justifica um banco de documentos.

**`$jsonSchema` em todas as coleções.** "Schema-less" não significa "sem regra": o
validador rejeita ticket sem `ticketCode` no formato certo ou com `status` fora do
enum. Sem isso, um bug de serialização entra silenciosamente no banco.

**TTL em `processed_events`.** Depois de 30 dias uma reentrega é implausível; sem o
TTL a coleção cresceria para sempre. As tabelas equivalentes no Postgres precisam de
uma limpeza agendada para o mesmo fim — está anotado como dívida no README.

---

## Estratégia de migrations

Flyway, com as migrations dentro de `src/main/resources/db/migration` de cada
serviço — cada serviço é dono do seu schema, e em produção a migration roda junto
com o boot da aplicação.

O `db/seed` do Order Service fica **fora** das locations padrão. Só o
docker-compose local adiciona esse diretório, então o catálogo de demonstração
nunca vaza para produção. Sendo `R__` (repeatable), roda de novo sempre que muda —
por isso todo `INSERT` lá usa `ON CONFLICT DO NOTHING`.
