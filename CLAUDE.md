# CLAUDE.md

Contexto para sessões futuras neste repositório. Ler antes de gerar código.

## O que é

TicketFlow — sistema distribuído de venda de ingressos, projeto de portfólio. Cada
decisão técnica existe para demonstrar uma competência específica. Se uma solução
mais simples resolveria o problema mas esconderia a competência, prefira a que
mantém a competência visível.

Detalhes em [README.md](README.md) e [docs/](docs/).

## A regra que não se quebra

**Nenhuma chamada síncrona entre Order, Payment e Notification Service.** Eles se
comunicam exclusivamente por eventos Kafka. Se aparecer um `RestTemplate`,
`WebClient` ou `FeignClient` apontando de um serviço para outro, é bug de
arquitetura — o único HTTP de saída permitido é o Payment Service chamando o
gateway externo.

## Idioma

- **Código, identificadores, comentários em código, nomes de tabela, rotas e campos
  JSON: inglês.** `Order`, `Payment`, `orders`, `/api/v1/orders`, `customerId`.
- **Documentação (`docs/`, README): português.**
- **Exceção única:** os tipos de evento `PAGAMENTO_APROVADO` e `PAGAMENTO_RECUSADO`
  são vocabulário fixo do projeto e ficam em português. Não criar sinônimos nem
  traduzir.

## Convenções de código

**Clean Architecture.** UseCase não conhece Spring, JPA nem Kafka. Recebe e devolve
objeto de domínio. Controller, repositório, cliente HTTP e binding de Kafka são
infraestrutura e ficam fora do caso de uso.

**SOLID de verdade.** Método de pagamento novo entra como uma `PaymentStrategy`
nova, não como mais um ramo de `if/else`. Injeção por construtor, nunca
`@Autowired` em campo.

**Testes.** Todo UseCase tem teste unitário (JUnit + Mockito). Toda integração
externa tem teste com Wiremock cobrindo sucesso, recusa, timeout e 5xx — caminho
feliz sozinho não conta como coberto.

**Idempotência.** Todo consumidor Kafka checa `processed_events` antes de agir.
Entrega é at-least-once; assumir exactly-once é defeito.

**Publicação de evento.** Sempre pelo outbox, na mesma transação da escrita de
negócio. Nunca `kafkaTemplate.send()` direto de dentro de um UseCase.

**Bancos.** Dado transacional → PostgreSQL. Documento/histórico → MongoDB. Não
force uma entidade transacional para o Mongo só porque já existe código Mongo por
perto.

**Dinheiro.** `BigDecimal` no Java, `NUMERIC(12,2)` no Postgres. Nunca `double`.

**Dados de cartão.** Não persistir, não logar, não publicar em evento. Só bandeira
e últimos 4 dígitos.

## Ao gerar código

Explique brevemente onde a peça se encaixa: qual serviço, qual evento produz ou
consome, qual banco toca. O projeto tem objetivo pedagógico e essa amarração é
parte da entrega.

## Ao revisar código

Além de bugs, apontar: UseCase acoplado a framework, `if/else` onde cabia Strategy,
teste que só cobre caminho feliz, consumidor sem checagem de idempotência,
publicação fora do outbox.

## Ambiente local

- Windows. Terminal padrão é PowerShell 5.1 — sem `&&`, sem operador ternário.
- Postgres do projeto no host é **5433** (a 5432 é de uma instalação nativa que já
  existe na máquina). Dentro da rede Docker continua `postgres:5432`.
- **JDK 21 está em `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`, mas o
  PATH aponta para um JRE 8.** Antes de qualquer build:
  `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"`.
  Maven não está instalado e não precisa estar — use `.\mvnw.cmd`.
- **O Docker aqui é o Rancher Desktop**, não Docker Desktop (`docker.exe` vem de
  `C:\Program Files\Rancher Desktop\...`). Se o `docker` não responder, é porque o
  Rancher Desktop não está aberto — ele precisa ser iniciado pela interface.
- **Testcontainers não consegue falar com o Docker nesta máquina.** Todos os named
  pipes devolvem um `/info` vazio para clientes JVM, embora o `docker` CLI funcione —
  é uma incompatibilidade do Rancher Desktop com o docker-java.
  Rodar os testes de integração assim:
  `.\mvnw.cmd verify "-Dticketflow.it.datasource.url=jdbc:postgresql://localhost:5433/ticketflow_orders_it"`.
  Esse banco é apagado a cada teste — nunca apontar para `ticketflow_orders`.
  No Notification Service o equivalente é o Mongo, e a credencial **precisa ser a
  root**: o usuário `ticketflow` só tem permissão no banco de demonstração, e
  apontar para o `_it` com ele falha com `not authorized`, não com erro de conexão.
  `.\mvnw.cmd verify "-Dticketflow.it.mongo.uri=mongodb://root:root@localhost:27017/ticketflow_notifications_it?authSource=admin"`
- Migrations rodam via container Flyway no compose e também no boot da aplicação.
  Os arquivos vivem em `src/main/resources/db/migration` de cada serviço.

## Padrões já estabelecidos pelo Order Service

Os outros dois serviços devem copiar estas escolhas, não reinventá-las:

- Projeto Maven independente, sem parent pom e sem módulo de eventos compartilhado.
- Pacotes `domain` / `application` / `infrastructure`, com dependência só para
  dentro. Casos de uso são classes simples, montadas em `UseCaseConfiguration` —
  nada de `@Service` na camada de aplicação.
- Transação via porta `UnitOfWork`, nunca `@Transactional` no caso de uso.
- Erros de domínio herdam de `DomainException` com um `code` estável; o
  `GlobalExceptionHandler` traduz código → status HTTP e devolve `ProblemDetail`.
- Surefire roda `*Test` (rápidos, sem Docker); Failsafe roda `*IT`.
- Sem Lombok. Records para DTOs, classes escritas à mão para o domínio.
- Kafka via Spring Cloud Stream: `StreamBridge` para publicar (o relay envia quando
  tem o que enviar), bean `Consumer<Message<String>>` para consumir. O parsing do
  envelope JSON fica no listener, nunca no caso de uso.
- Testes de mensageria com **EmbeddedKafka**, não Testcontainers — sem Docker,
  rodam aqui e na CI. Os `*IT` herdam de `support/OrderServiceIT`, que já cuida do
  banco e do catálogo.
- **`@Scheduled` e `@Transactional` nunca na mesma classe.** O timer chamaria o
  método em `this` e passaria por fora do proxy — o relay quebrou exatamente assim,
  com "no transaction is known to be in progress", e só apareceu rodando de verdade.
  O gatilho mora num bean separado.
- **Um arquivo por interface de repositório Spring Data.** Agrupar várias como
  interfaces aninhadas dentro de uma classe faz o scan não encontrá-las, e o erro só
  aparece no boot como "No qualifying bean".
- **Dentro de `await().untilAsserted()`, nunca `jdbc.queryForObject`.** Ele lança
  `EmptyResultDataAccessException` quando não há linha, e o Awaitility só repete em
  `AssertionError` — a espera aborta na primeira tentativa e o sintoma engana, parece
  que o consumidor nunca rodou. Usar `queryForList` e devolver null.
- **Flag perigosa tem padrão seguro, e segredo não tem padrão nenhum.**
  `ticketflow.auth.dev-tokens` já valeu `true` na configuração base — subir para
  qualquer lugar sem lembrar da variável deixava um emissor de identidade aberto na
  internet, emitindo token para qualquer e-mail sem senha. Hoje o padrão é `false`
  e os perfis `local` e `docker` ligam explicitamente. `ticketflow.auth.secret` não
  tem padrão fora desses dois perfis: variável ausente derruba o boot, porque um
  ambiente rodando com a chave de exemplo publicada aqui aceita token forjado.
- **A identidade do cliente é derivada do token, não é o `sub` cru.** O domínio usa
  UUID; provedor de identidade não é obrigado a usar — o Google devolve um número.
  `AuthenticatedCustomer.customerId` e o `CustomerIdentity` do Notification Service
  aplicam a **mesma** regra (`sub` que já é UUID passa direto; senão deriva de
  `issuer|sub`). Divergir entre os dois faz o Order gravar com um id e o
  Notification buscar com outro: o ingresso some da tela sem erro em lugar nenhum.
  Os dois lados têm um teste com o mesmo vetor fixo justamente para isso quebrar o
  build.
- **Recusa (`REJECTED`) e falha (`FAILED`) são coisas diferentes** e o Payment
  Service depende disso: recusa publica `PAGAMENTO_RECUSADO`; timeout ou 5xx não
  publicam nada, não gravam no inbox e deixam a mensagem ser reentregue. Nunca
  transformar "o gateway não respondeu" em "o cartão foi negado".

## Roadmap

Quatro fases, descritas no [README](README.md#roadmap). Fase 1 concluída; na fase 2
o Order Service está pronto e testado, faltando o relay do outbox, o Payment Service
e o Notification Service. Ao ser perguntado "o que vem agora", localizar a fase pelo
que já existe no repositório e sugerir o próximo item **dessa** fase, sem pular para
a seguinte.
