# TicketFlow

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

### O ciclo assíncrono, na prática

Sem o Payment Service existir, dá para ver o fluxo inteiro:

1. `POST /orders` → **202**, pedido `PENDING`, evento gravado no outbox na mesma
   transação.
2. O relay acorda (1s), pega as linhas `PENDING` com `FOR UPDATE SKIP LOCKED` —
   é isso que permite rodar várias instâncias sem publicar a mesma mensagem duas
   vezes — e publica em `ticketflow.orders.created` com a chave igual ao `orderId`.
3. Publique um `PAGAMENTO_APROVADO` à mão pelo [Kafka UI](http://localhost:8085) em
   `ticketflow.payments.processed`, com `data.orderId` igual ao pedido.
4. `GET /orders/{id}` → **`PAID`**, com a timeline mostrando `PENDING -> PAID`, e o
   estoque migrando de `reserved` para `sold`.
5. Publique a mesma mensagem de novo: nada muda. O `eventId` já está em
   `processed_events` e o consumidor descarta.

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

- [ ] Actuator + Micrometer + OpenTelemetry
- [ ] Prometheus e Grafana no compose, com dashboard
- [ ] Manifestos Kubernetes (Deployment + Service por microsserviço)
- [ ] LocalStack para simular AWS

### Fase 4 — CI/CD e frontend

- [ ] Pipeline: build Maven, testes, imagem Docker
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
