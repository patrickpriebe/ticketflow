#!/usr/bin/env bash
# Cria o bucket dos ingressos assim que o LocalStack fica pronto.
# Roda uma vez, via /etc/localstack/init/ready.d.
set -euo pipefail

awslocal s3 mb s3://ticketflow-tickets

# Versionamento: um ingresso arquivado nunca deveria ser sobrescrito em silêncio.
awslocal s3api put-bucket-versioning \
  --bucket ticketflow-tickets \
  --versioning-configuration Status=Enabled

echo "[ticketflow] bucket ticketflow-tickets pronto"
