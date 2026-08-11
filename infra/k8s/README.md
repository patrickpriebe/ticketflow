# Kubernetes (Minikube)

Um `Deployment` e um `Service` por microsserviço, mais o namespace, o ConfigMap e o
Secret. A infraestrutura (Postgres, Mongo, Kafka) **não** está aqui: rodar banco
com estado em Minikube é um projeto à parte, e não é o que estes manifestos querem
demonstrar.

## O que esses manifestos mostram

**Duas réplicas de cada serviço, e isso não é enfeite.** É o que exercita as
decisões tomadas no código:

- o relay do outbox usa `FOR UPDATE SKIP LOCKED` justamente para que duas
  instâncias não publiquem a mesma mensagem;
- os consumidores são idempotentes porque o rebalanceamento do Kafka reentrega;
- cobrar duas vezes o mesmo pedido é impedido pelo inbox e pela constraint `UNIQUE`
  em `payments.order_id`, não pelo número de réplicas.

**Três probes, com trabalhos diferentes.** Trocá-las causa estrago de verdade:
`startup` dá tempo ao boot sem afrouxar as outras, `readiness` tira do balanceador
sem matar o pod, `liveness` reinicia. Todas apontam para os grupos que o Actuator já
expõe (`health.probes.enabled: true`).

**Nenhum default no perfil `kubernetes`.** Variável faltando quebra o boot em vez de
conectar silenciosamente em algo inesperado.

## Rodando

```bash
minikube start --cpus=4 --memory=6g
```

Construir as imagens **dentro do daemon do Minikube**, já que `imagePullPolicy` é
`IfNotPresent` e não há registry:

```bash
eval $(minikube docker-env)
docker build -f services/order-service/Dockerfile -t ticketflow/order-service:0.1.0 .
docker build -f services/payment-service/Dockerfile -t ticketflow/payment-service:0.1.0 .
docker build -f services/notification-service/Dockerfile -t ticketflow/notification-service:0.1.0 .
```

Aplicar:

```bash
kubectl apply -f infra/k8s/
```

Acompanhar:

```bash
kubectl -n ticketflow get pods -w
```

Alcançar a API:

```bash
kubectl -n ticketflow port-forward svc/order-service 8080:8080
```

## Antes de aplicar, o que falta

Os pods vão ficar em `CrashLoopBackOff` até existirem `postgres`, `mongo` e `kafka`
resolvíveis dentro do namespace — os serviços tentam conectar no boot e o Flyway
falha rápido, de propósito.

Para um ambiente completo no Minikube, o caminho mais curto é instalar as
dependências por Helm (`bitnami/postgresql`, `bitnami/mongodb`, `bitnami/kafka`) com
os nomes de serviço que o ConfigMap espera, ou apontar o ConfigMap para uma
infraestrutura que já esteja rodando fora do cluster.

## Honestidade sobre o Secret

`ticketflow-secrets` está em texto no repositório. Um `Secret` do Kubernetes é
apenas base64, não é criptografia. Num ambiente real isto seria External Secrets,
Sealed Secrets ou Vault, e jamais estaria versionado. Está aqui porque as
credenciais são descartáveis e o objetivo é rodar no Minikube.
