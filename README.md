# TicketFlow

[![CI](https://github.com/patrickpriebe/ticketflow/actions/workflows/ci.yml/badge.svg)](https://github.com/patrickpriebe/ticketflow/actions/workflows/ci.yml)

Sistema distribuído de venda de ingressos: três microsserviços Spring Boot que
conversam só por eventos, dois bancos com papéis diferentes, e infraestrutura que
sobe inteira com um comando.

O ponto do projeto é uma decisão só: **o pedido é aceito sem esperar o pagamento**.
A API responde `202 Accepted` em milissegundos, o pagamento acontece depois em outro
processo, e o cliente acompanha o status. Tudo o mais no repositório existe para
sustentar essa premissa.

- [Arquitetura](docs/01-arquitetura.md) — por que Kafka, por que outbox, por que dois bancos
- [Modelagem de dados](docs/02-modelagem-dados.md) — as tabelas, as coleções e o motivo de cada escolha
- [Eventos Kafka](docs/03-eventos-kafka.md) — tópicos, envelope, retry, DLQ
- [Contrato da API](contracts/openapi/order-service.yaml) — OpenAPI 3.0 do Order Service

---

## Como subir

Só precisa de **Docker Desktop**. Na raiz do repositório:

```bash
docker compose up -d
```

Sobe PostgreSQL, MongoDB e Kafka, aplica as migrations, cria os tópicos e abre o
Kafka UI. As portas dão para ajustar copiando `.env.example` para `.env`.

Para derrubar tudo:

```bash
docker compose down
```

Para derrubar **e apagar os dados** (faz os scripts de init rodarem de novo):

```bash
docker compose down -v
```

### O que fica exposto

| Serviço | Endereço | Credenciais |
|---|---|---|
| PostgreSQL | `localhost:5433` | `ticketflow` / `ticketflow` |
| MongoDB | `localhost:27017` | `root` / `root` (app: `ticketflow` / `ticketflow`) |
| Kafka | `localhost:9092` | — |
| Kafka UI | http://localhost:8085 | — |
| Prometheus | http://localhost:9091 | perfil `observability` |
| Grafana | http://localhost:3002 | perfil `observability`, entra direto |

> **Postgres está em 5433, não 5432.** A 5432 costuma já estar tomada por uma
> instalação nativa de PostgreSQL na máquina. Dentro da rede Docker o serviço
> continua em `postgres:5432` — só o mapeamento para o host muda.

São credenciais de desenvolvimento local. Não servem para nada além disso.

### Conferindo que subiu

```bash
docker compose ps
```

```bash
docker exec ticketflow-postgres psql -U ticketflow -d ticketflow_orders -c "\dt"
```

```bash
docker exec ticketflow-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --list
```

O catálogo já vem com três eventos de demonstração — o seed em
`db/seed/R__seed_demo_catalogue.sql` só é aplicado no ambiente local.

---

## Order Service

A API de pedidos. O que ela faz de interessante cabe em uma frase: `POST /orders`
grava o pedido e o evento `ORDER_CREATED` **na mesma transação** e responde `202` —
ninguém foi cobrado ainda.

### Rodando

Precisa de JDK 21. Com a infraestrutura de pé (`docker compose up -d`):

```bash
cd services/order-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- API: http://localhost:8080/api/v1/events
- Swagger UI: http://localhost:8080/swagger-ui.html (servindo o contrato da fase 1)
- Health: http://localhost:8080/actuator/health

### Testes

```bash
cd services/order-service && ./mvnw test
```

São 38 testes unitários, sem Docker e sem contexto Spring — domínio puro e casos de
uso com fakes. Os de integração sobem um PostgreSQL de verdade:

```bash
cd services/order-service && ./mvnw verify
```

> **Se o Testcontainers não achar o Docker no Windows**, aponte os testes de
> integração para um banco já rodando. Com o Rancher Desktop a Engine API não fica
> acessível para clientes JVM, mesmo com o CLI funcionando normalmente — foi o caso
> nesta máquina. Crie o banco uma vez:
>
> ```bash
> docker exec ticketflow-postgres psql -U ticketflow -d postgres -c "CREATE DATABASE ticketflow_orders_it"
> ```
>
> e rode:
>
> ```bash
> cd services/order-service && ./mvnw verify -Dticketflow.it.datasource.url=jdbc:postgresql://localhost:5433/ticketflow_orders_it
> ```
>
> O banco apontado é **apagado** a cada teste; nunca use o `ticketflow_orders`.

### Como o código está organizado

```
domain/          entidades e regras. Não importa Spring, JPA nem Kafka.
application/     casos de uso + portas (interfaces). Também sem framework.
infrastructure/  web, persistência, outbox, configuração. Todo o Spring vive aqui.
```

Três detalhes que valem explicar numa entrevista:

- **`UnitOfWork` em vez de `@Transactional` no caso de uso.** O `INSERT` do pedido e
  o do outbox precisam da mesma transação, mas anotar o caso de uso o acoplaria ao
  Spring. A porta `UnitOfWork` é implementada com `TransactionTemplate` na
  infraestrutura, e o teste unitário injeta uma versão que só executa o bloco.
- **Idempotência pela constraint, não por `SELECT` antes.** O `SELECT` é só
  otimização; quem garante é `uq_orders_idempotency_key`. Se duas requisições com a
  mesma chave correm juntas, a que perde recarrega o pedido da vencedora e devolve
  `200`. Tem teste para essa corrida.
- **O relay publica depois de commitar, nunca antes.** Isso torna a entrega
  *at-least-once*: se o processo cair entre o envio e o `UPDATE`, a mensagem sai duas
  vezes. É a troca certa — duplicata os consumidores tratam, evento perdido ninguém
  recupera.

---

## Vendo o sistema inteiro funcionar

Sobe tudo, incluindo os dois serviços e o gateway simulado:

```bash
docker compose --profile apps up -d --build
```

Faça dois pedidos. O gateway simulado recusa qualquer cobrança acima de 2000, então
um **Camarote** (2400) é negado e uma **Pista** (650) é aprovada — os dois ramos do
fluxo, sem configurar nada:

```bash
curl -X POST http://localhost:8081/api/v1/orders -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" -d '{"customer":{"id":"3f1c9a6e-77b2-4c0d-9f31-2a5b8e4d6c10","name":"Ana Souza","email":"ana.souza@example.com"},"eventId":"11111111-1111-4111-8111-111111111111","paymentMethod":"CREDIT_CARD","items":[{"ticketCategoryId":"aaaaaaaa-0001-4000-8000-000000000001","quantity":2}]}'
```

A resposta é **202** com `status: PENDING`. Poucos segundos depois:

```bash
curl http://localhost:8081/api/v1/orders/{id}
```

O que aconteceu nesse intervalo, sem nenhuma chamada síncrona entre serviços:

```
POST /orders ──► pedido PENDING + evento no outbox   (mesma transação)
                          │
                 relay, ~1s depois
                          ▼
              ticketflow.orders.created   (chave = orderId)
                          │
                          ▼
              Payment Service cobra o gateway
                          │
                          ▼
            ticketflow.payments.processed
                          │
                          ▼
          pedido vira PAID ou REJECTED, estoque acerta
```

Resultado real dos dois pedidos:

| Pedido | Valor | Gateway | Status final |
|---|---|---|---|
| Pista ×2 | 1300,00 | aprovado | **`PAID`**, reserva vira venda |
| Camarote ×1 | 2400,00 | recusado | **`REJECTED`**, ingressos voltam ao estoque |

Para ver as mensagens cruas, o [Kafka UI](http://localhost:8085); para ver o que o
Payment Service enviou ao gateway, incluindo o `Idempotency-Key`,
http://localhost:8090/__admin/requests.

---

## Observabilidade

```bash
docker compose --profile apps --profile observability up -d
```

Grafana em http://localhost:3002 já sobe com o datasource e o dashboard
**TicketFlow — visão geral** provisionados. Sem login, sem clicar em nada.

O dashboard responde a perguntas que só existem porque o sistema é assíncrono:

- **Backlog do outbox** — o número mais útil do sistema. Se `PENDING` sobe, o relay
  parou ou o broker sumiu, e a API continua respondendo `202` alegremente enquanto
  ninguém a jusante fica sabendo. É uma falha invisível pela API.
- **Resultado das cobranças** — aprovado e recusado são respostas do gateway;
  `TIMEOUT` e `ERROR` não são, e são o par que sobe primeiro quando o provedor
  começa a falhar.
- **Latência do gateway** — o `read-timeout` é 5s; quando o p99 encosta nele, os
  timeouts começam.
- **Latência do `POST /orders`** — tem que ser baixa justamente porque não espera o
  pagamento. Se subir, a premissa do projeto está sendo violada em algum lugar.

As métricas de negócio são registradas na infraestrutura, nunca nos casos de uso —
`OutboxMetrics` e o timer no cliente do gateway. O domínio não sabe que Prometheus
existe.

---

## Payment Service

Um worker sem API pública. Consome `ORDER_CREATED`, cobra pelo gateway externo e
publica `PAGAMENTO_APROVADO` ou `PAGAMENTO_RECUSADO`. É o único serviço que faz HTTP
de saída — e só para o gateway.

```bash
cd services/payment-service && ./mvnw verify
```

Duas ideias sustentam o serviço:

- **Uma `PaymentStrategy` por método.** Adicionar um método significa escrever uma
  implementação nova; nenhum arquivo existente muda de comportamento. O registry se
  recusa a subir se algum método ficar sem estratégia — erro no boot, não na compra
  de um cliente. É também a estratégia que decide se um timeout pode ser retentado:
  cartão e PIX sim, boleto não, porque geraria um segundo boleto.
- **Recusa e falha não são a mesma coisa.** `REJECTED` é o gateway dizendo não, e
  publica `PAGAMENTO_RECUSADO`. `FAILED` é timeout ou 5xx: **não publica nada**, não
  grava no inbox e deixa a mensagem ser reentregue. Ninguém sabe se o dinheiro se
  moveu, e dizer ao cliente que o cartão foi negado seria mentira.

Os testes com Wiremock cobrem os quatro cenários — aprovado, recusado, timeout e
5xx — porque caminho feliz sozinho não é cobertura. O gateway simulado do ambiente
local está documentado em [`infra/wiremock/`](infra/wiremock/README.md).

---

## Estrutura

```
ticketflow/
├── contracts/
│   ├── openapi/          contrato REST do Order Service
│   └── events/           JSON Schema dos eventos Kafka
├── docs/                 arquitetura, modelagem, eventos
├── infra/
│   ├── postgres/init/    criação dos databases
│   ├── mongo/init/       coleções, validadores e índices
│   └── kafka/            criação dos tópicos
├── services/
│   ├── order-service/    API REST — implementado
│   ├── payment-service/  worker   — implementado
│   └── notification-service/      — a fazer
└── docker-compose.yml
```

Cada serviço é um projeto Maven independente, com seu próprio `pom.xml` e `mvnw`.
Não há parent pom nem módulo de eventos compartilhado: acoplar os três em tempo de
compilação anularia boa parte do sentido de separá-los em tempo de execução.

---

## Roadmap

### Fase 1 — Fundação e modelagem ✅

- [x] Contrato OpenAPI do Order Service
- [x] Schemas dos eventos Kafka (envelope + payloads)
- [x] Modelagem PostgreSQL dos dois serviços, com Flyway
- [x] Modelagem MongoDB com validadores e índices
- [x] docker-compose com Postgres + Mongo + Kafka de pé

### Fase 2 — Backend em Clean Architecture

- [x] Order Service com Spring Boot e TDD — domínio, casos de uso, REST e outbox
- [x] Spring Cloud Stream ligando o Order Service ao Kafka
- [x] Relay do outbox, publicando com `FOR UPDATE SKIP LOCKED`
- [x] Consumo do resultado do pagamento, idempotente
- [x] Payment Service, com Strategy por método de pagamento
- [x] Testes com Wiremock: aprovado, recusado, timeout, 5xx
- [ ] Notification Service gravando ingresso no MongoDB

### Fase 3 — Observabilidade e nuvem

- [x] Actuator + Micrometer nos três serviços, com métricas de negócio
- [x] Prometheus e Grafana no compose, com dashboard provisionado
- [x] Manifestos Kubernetes (Deployment + Service por microsserviço)
- [x] LocalStack com S3 arquivando os ingressos emitidos

### Fase 4 — CI/CD e frontend

- [x] Pipeline: build Maven, testes, lint dos contratos, imagem Docker
- [ ] Telas React: lista de eventos e status do pedido em tempo real

---

## Ferramentas

- **JDK 21** (Temurin). Se `java -version` mostrar Java 8, o PATH está apontando
  para o JRE antigo — aponte `JAVA_HOME` para o JDK 21 antes de buildar.
- **Maven não precisa ser instalado**: cada serviço traz o Maven Wrapper (`mvnw`),
  que baixa a versão certa sozinho.
- **Docker Desktop**, para a infraestrutura.

---

## Dívidas conhecidas

Anotadas para não parecerem esquecimento:

- As tabelas `processed_events` no Postgres crescem sem limite. A do MongoDB já tem
  TTL de 30 dias; as do Postgres precisam de uma limpeza agendada.
- O relay do outbox ainda não existe (fase 2). Sem ele, nada é publicado no Kafka.
- Não há autenticação em nada — `customerId` chega no corpo da requisição. Um
  sistema real teria JWT e tiraria a identidade do token, nunca do payload.
- `ticket_categories` tem contadores de estoque, mas a reserva efetiva no momento do
  pedido é trabalho da fase 2.
