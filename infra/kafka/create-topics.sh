#!/usr/bin/env bash
# =============================================================================
# Creates the TicketFlow topics.
#
# auto.create.topics.enable is off on the broker on purpose: a topic that
# appears by accident (typo in a producer) is a bug that should fail loudly,
# not something the broker silently papers over.
#
# Partitions: 3 for the business topics so consumers can scale out. The key is
# always the orderId, which keeps every event of the same order on the same
# partition and therefore strictly ordered.
# Replication: 1, because this is a single-node local broker.
# =============================================================================
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka:19092}"
KAFKA_BIN="${KAFKA_BIN:-/opt/kafka/bin}"

create_topic() {
    local name="$1" partitions="$2" retention_ms="$3"

    "${KAFKA_BIN}/kafka-topics.sh" \
        --bootstrap-server "${BOOTSTRAP}" \
        --create --if-not-exists \
        --topic "${name}" \
        --partitions "${partitions}" \
        --replication-factor 1 \
        --config retention.ms="${retention_ms}" \
        --config min.insync.replicas=1
    echo "  ok  ${name} (partitions=${partitions})"
}

echo "[ticketflow] waiting for broker at ${BOOTSTRAP}..."
until "${KAFKA_BIN}/kafka-topics.sh" --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; do
    sleep 2
done

echo "[ticketflow] creating topics"

# Order Service  ->  Payment Service
create_topic "ticketflow.orders.created"          3 604800000    # 7 days

# Payment Service  ->  Order Service + Notification Service
create_topic "ticketflow.payments.processed"      3 604800000    # 7 days

# Order Service  ->  Payment Service (compensation trigger)
# Same 3 partitions and the same key as orders.created, so every event about one
# order lands on the same partition. Kafka orders within a partition, not across
# topics - the Payment Service still has to cope with a cancellation arriving
# before or after it charged the card.
create_topic "ticketflow.orders.cancelled"        3 604800000    # 7 days

# Dead letter topics: kept much longer, they are the debugging trail.
create_topic "ticketflow.orders.created.dlq"      1 2592000000   # 30 days
create_topic "ticketflow.payments.processed.dlq"  1 2592000000   # 30 days

echo "[ticketflow] topics now on the broker:"
"${KAFKA_BIN}/kafka-topics.sh" --bootstrap-server "${BOOTSTRAP}" --list | sed 's/^/  - /'
