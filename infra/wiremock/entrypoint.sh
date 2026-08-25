#!/bin/sh
# ==============================================================================
# Decide se a API de administração do Wiremock fica alcançável.
#
# A regra segue a que o resto do projeto já aprendeu doendo: **o padrão é o
# seguro**. Variável ausente aqui significa trancado, não aberto. `dev-tokens` e
# `/actuator/prometheus` custaram um susto cada por terem sido escritos ao
# contrário — um emissor de identidade e um endpoint de métricas expostos porque
# alguém esqueceu de sobrescrever.
#
# Aberto exige um "sim" explícito: GATEWAY_ADMIN_OPEN=true. É o que o compose
# passa, porque ver as requisições que o Payment Service enviou é metade da
# utilidade deste contêiner em desenvolvimento.
#
# Fora isso, ou o ambiente entrega credencial em GATEWAY_ADMIN_AUTH
# (`usuario:senha`), ou uma senha aleatória é sorteada no boot e nunca sai
# daqui — trancado de fato, sem exigir configuração para ser seguro.
# ==============================================================================
set -e

if [ "$GATEWAY_ADMIN_OPEN" = "true" ]; then
  ADMIN_ARGS=""
  echo "gateway: API de administração aberta (GATEWAY_ADMIN_OPEN=true)"
else
  if [ -z "$GATEWAY_ADMIN_AUTH" ]; then
    GATEWAY_ADMIN_AUTH="admin:$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')"
    echo "gateway: sem GATEWAY_ADMIN_AUTH — senha aleatória sorteada, administração inacessível"
  else
    echo "gateway: API de administração protegida por credencial do ambiente"
  fi
  ADMIN_ARGS="--admin-api-basic-auth $GATEWAY_ADMIN_AUTH"
fi

# `--global-response-templating` permite a uma regra devolver um id gerado em vez
# de um valor fixo. Sem ele toda cobrança voltaria com o mesmo `transactionId` e
# a constraint de idempotência recusaria a segunda.
exec /docker-entrypoint.sh \
  --port "${PORT:-8080}" \
  --global-response-templating \
  --disable-banner \
  $ADMIN_ARGS
