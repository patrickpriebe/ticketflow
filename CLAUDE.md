# CLAUDE.md

Context for future sessions in this repository. Read before generating code.

## What this is

TicketFlow — a distributed ticket-selling system, built as a portfolio project. Every
technical decision exists to demonstrate a specific competency. If a simpler solution
would solve the problem but hide the competency, prefer the one that keeps the
competency visible.

Details in [README.md](README.md) and [docs/](docs/).

## The rule that is never broken

**No synchronous call between Order, Payment and Notification.** They communicate
exclusively through Kafka events. If a `RestTemplate`, `WebClient` or `FeignClient`
ever points from one service to another, that is an architecture bug — the only
permitted outbound HTTP is the Payment Service calling the external gateway.

**The browser is not a service.** It talks to all three directly, and that does not
break the rule. This is how Stripe Elements gets the `client_secret`: it is born in
the Payment Service, and the browser fetches it from there. Routing it through the
Order Service would have required either service-to-service HTTP or shipping a payment
credential through a Kafka topic into another service's database.

## Language

- **Code, identifiers, table names, routes and JSON fields: English.** `Order`,
  `Payment`, `orders`, `/api/v1/orders`, `customerId`.
- **Code comments and Javadoc: Portuguese.** Some older backend code is in English;
  do not translate it just to be consistent, but write new comments in Portuguese —
  that is what the codebase reads like today.
- **Markdown documentation (`README.md`, `docs/`): English.** This is a public
  portfolio and the audience reads English.
- **Single exception:** the event types `PAGAMENTO_APROVADO` and `PAGAMENTO_RECUSADO`
  are fixed project vocabulary and stay in Portuguese. Do not create synonyms or
  translate them.

## Code conventions

**Clean Architecture.** A use case knows nothing about Spring, JPA or Kafka. It takes
and returns domain objects. Controllers, repositories, HTTP clients and Kafka bindings
are infrastructure and live outside the use case.

**Real SOLID.** A new payment method arrives as a new `PaymentStrategy`, not as another
branch in an `if/else`. Constructor injection, never `@Autowired` on a field.

**Tests.** Every use case has a unit test (JUnit + Mockito). Every external integration
has a WireMock test covering success, decline, timeout and 5xx — the happy path alone
does not count as covered.

**Idempotency.** Every Kafka consumer checks `processed_events` before acting. Delivery
is at-least-once; assuming exactly-once is a defect.

**Publishing events.** Always through the outbox, in the same transaction as the
business write. Never `kafkaTemplate.send()` from inside a use case.

**Databases.** Transactional data → PostgreSQL. Documents and history → MongoDB. Do not
force a transactional entity into Mongo just because there is Mongo code nearby.

**Money.** `BigDecimal` in Java, `NUMERIC(12,2)` in PostgreSQL. Never `double`.

**Card data.** Never persisted, never logged, never published in an event. Only the
brand and the last four digits.

## When generating code

Briefly explain where the piece fits: which service, which event it produces or
consumes, which database it touches. The project is pedagogical and that connection is
part of the deliverable.

## When reviewing code

Beyond bugs, call out: a use case coupled to a framework, an `if/else` where a Strategy
belongs, a test that only covers the happy path, a consumer without an idempotency
check, and publishing outside the outbox.

## Local environment

- Windows. The default terminal is PowerShell 5.1 — no `&&`, no ternary operator.
- The project's PostgreSQL is on **5433** on the host (5432 belongs to a native install
  that already exists on this machine). Inside the Docker network it is still
  `postgres:5432`.
- **JDK 21 lives at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`, but PATH
  points at a JRE 8.** Before any build:
  `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"`.
  Maven is not installed and does not need to be — use `.\mvnw.cmd`.
- **Docker here is Rancher Desktop**, not Docker Desktop (`docker.exe` comes from
  `C:\Program Files\Rancher Desktop\...`). If `docker` does not respond, Rancher Desktop
  is not running — it has to be started from its interface.
- **Testcontainers cannot talk to Docker on this machine.** Every named pipe returns an
  empty `/info` to JVM clients even though the `docker` CLI works — an incompatibility
  between Rancher Desktop and docker-java. Run the integration tests like this:
  `.\mvnw.cmd verify "-Dticketflow.it.datasource.url=jdbc:postgresql://localhost:5433/ticketflow_orders_it"`.
  That database is wiped on every test — never point it at `ticketflow_orders`.
  In the Notification Service the equivalent is Mongo, and the credential **must be
  root**: the `ticketflow` user only has rights on the demo database, and pointing it at
  the `_it` one fails with `not authorized`, not with a connection error.
  `.\mvnw.cmd verify "-Dticketflow.it.mongo.uri=mongodb://root:root@localhost:27017/ticketflow_notifications_it?authSource=admin"`
- Migrations run through the Flyway container in compose and also at application boot.
  The files live in `src/main/resources/db/migration` of each service.

## Patterns already established

The other services copy these choices rather than reinventing them.

- Independent Maven project, no parent POM, no shared events module.
- Packages `domain` / `application` / `infrastructure`, with dependencies pointing only
  inwards. Use cases are plain classes assembled in a configuration class — no
  `@Service` in the application layer.
- Transactions through a `UnitOfWork` port, never `@Transactional` on a use case.
- Domain errors extend `DomainException` with a stable `code`; the
  `GlobalExceptionHandler` translates code → HTTP status and returns a `ProblemDetail`.
- Surefire runs `*Test` (fast, no Docker); Failsafe runs `*IT`.
- No Lombok. Records for DTOs, hand-written classes for the domain.
- Kafka through Spring Cloud Stream: `StreamBridge` to publish (the relay sends when it
  has something to send), a `Consumer<Message<String>>` bean to consume. Parsing the
  JSON envelope belongs to the listener, never to the use case.
- Messaging tests use **EmbeddedKafka**, not Testcontainers — no Docker, so they run
  here and in CI. The `*IT` classes extend a support base that already handles the
  database and the catalogue.
- **`@Scheduled` and `@Transactional` never on the same class.** The timer would call
  the method on `this` and bypass the proxy — the relay broke exactly that way, with
  "no transaction is known to be in progress", and it only showed up running for real.
  The trigger lives in its own bean.
- **One file per Spring Data repository interface.** Grouping several as nested
  interfaces makes the scan miss them, and the error only appears at boot as "No
  qualifying bean".
- **Inside `await().untilAsserted()`, never `jdbc.queryForObject`.** It throws
  `EmptyResultDataAccessException` when there is no row, and Awaitility only retries on
  `AssertionError` — the wait aborts on the first attempt and the symptom lies, looking
  as if the consumer never ran. Use `queryForList` and return null.
- **A dangerous flag defaults to safe, and a secret has no default at all.**
  `ticketflow.auth.dev-tokens` was once `true` in the base configuration — deploying
  anywhere without remembering the variable left an identity issuer open on the
  internet, handing out tokens for any email without a password. Today the default is
  `false` and only the `local` and `docker` profiles turn it on.
- **With an external provider, `audience` is mandatory alongside `issuer-uri`.** A valid
  signature and the right issuer prove Google issued the token — not that it was issued
  for us. A legitimate token from any other Google application carries the same `iss`
  and the same `sub` for that person, so without comparing `aud` it logs them in here.
  All three services refuse to boot on issuer-without-audience, and
  `JwtDecoderSelectionTest` locks that on every side.
- **Customer identity is derived from the token, not the raw `sub`.** The domain uses
  UUIDs; an identity provider is not obliged to — Google returns a number. All three
  services apply the **same** rule (a `sub` that is already a UUID passes through,
  otherwise the id comes from `issuer|sub`). Diverging makes one service write with one
  id and another read with a different one: the ticket vanishes from the screen with no
  error anywhere. All three have a test with the same pinned vector so this breaks the
  build.
- **Decline (`REJECTED`), failure (`FAILED`) and accepted (`ACCEPTED`) are three
  different things** and the Payment Service depends on it: a decline publishes
  `PAGAMENTO_RECUSADO`; a timeout or 5xx publishes nothing, records nothing and lets the
  message be redelivered; `ACCEPTED` means the provider took it and the answer comes by
  webhook. Never turn "the gateway did not answer" into "the card was declined".
- **Not-yours and not-found answer the same.** Asking for another customer's order or
  charge returns exactly what a non-existent one returns. A `403` would confirm the
  resource exists to whoever is probing ids.
- **A value the client chooses is never looked up unscoped.** `Idempotency-Key` was
  unique table-wide, and since a repeated key replays the order that owns it, sending
  `Idempotency-Key: order-1` returned a stranger's order — name, e-mail and all. The
  key is now scoped to the customer in the query *and* in the unique constraint. The
  rule beyond this one bug: looking something up by a client-supplied value without
  scoping it to the caller is an authorisation decision made by accident.
- **The outbox row carries its own topic, and the publisher obeys it.** The relay used
  to send everything to one fixed binding, which worked only while there was a single
  topic. `ticketflow.outbox.bindings` maps topic → binding, and an unmapped topic
  throws instead of silently publishing to the wrong destination — a cancellation
  landing on `orders.created` would have the Payment Service charge a cancelled order.
- **Dangerous defaults are safe even when they cost nothing today.**
  `ticketflow.observability.public-metrics` is `false` by default; only the compose
  file and the Kubernetes ConfigMap turn it on. Open, `/actuator/prometheus` gives away
  order volume, approved and declined amounts, every route and the JVM version.

## Deployment traps that already cost a red deploy

- **A `sync: false` variable is not created by an automatic blueprint sync.** Render has
  nobody to ask for the value, so the variable is simply absent and the service fails to
  boot. That is the desired behaviour — but expect a red deploy when adding one, until
  it is filled in the panel.
- **Vite inlines `VITE_*` at build time.** Adding the variable in the Vercel panel does
  not change an already-published site; it needs a rebuild.
- **Managed free databases have low connection ceilings.** Supabase's free pooler accepts
  15; a pool of 10 per instance means the second instance to start dies during Flyway.
  The cloud profiles cap the pool at 5.
- **Environments must not share a Kafka consumer group.** With one broker for local and
  deployed, Kafka splits the partitions between them and half the orders are processed by
  the wrong environment, with no error anywhere. Groups carry `TICKETFLOW_GROUP_SUFFIX`.
- **Micrometer does not publish histogram buckets by default**, so every
  `histogram_quantile` in Grafana returns "No data" until
  `management.metrics.distribution.percentiles-histogram.*` is enabled. A panel that
  never has data is worse than no panel: it teaches people to ignore the dashboard.

## Roadmap

Four phases, described in the [README](README.md). Phases 1 through 4 are done: the
three services, the frontend, observability, CI/CD, Kubernetes manifests, and a
deployed environment on Render, Vercel, Supabase, Neon, Atlas and Redpanda, with Google
sign-in and Stripe.

When asked "what comes next", locate the phase from what already exists in the
repository and suggest the next item **of that phase**, without jumping ahead. The
current open items are listed under "Known limits" in the README — order cancellation
with compensation is the most substantial one.
