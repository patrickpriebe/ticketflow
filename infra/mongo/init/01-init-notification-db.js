/*
 * =============================================================================
 * Notification Service - MongoDB bootstrap
 * Database: ticketflow_notifications
 *
 * Runs once, on a fresh volume, via /docker-entrypoint-initdb.d.
 * To re-run it: docker compose down -v && docker compose up -d
 *
 * Mongo holds the *document* side of the system: issued tickets and the
 * notification history. Anything transactional (orders, payments) stays in
 * PostgreSQL - see docs/02-data-model.md for the reasoning.
 * =============================================================================
 */

const notificationsDb = db.getSiblingDB('ticketflow_notifications');

// -----------------------------------------------------------------------------
// Application user, scoped to this database only.
// -----------------------------------------------------------------------------
notificationsDb.createUser({
    user: 'ticketflow',
    pwd: 'ticketflow',
    roles: [{ role: 'readWrite', db: 'ticketflow_notifications' }]
});

// -----------------------------------------------------------------------------
// tickets - generated once a payment is approved. Carries a snapshot of the
// event so a ticket stays readable even if the catalogue changes later.
// -----------------------------------------------------------------------------
notificationsDb.createCollection('tickets', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['ticketCode', 'orderId', 'eventId', 'holder', 'status', 'issuedAt'],
            additionalProperties: true,
            properties: {
                ticketCode: {
                    bsonType: 'string',
                    pattern: '^TF-[A-Z0-9]{10}$',
                    description: 'Human-readable code printed on the ticket, e.g. TF-9F2K1A7B3C'
                },
                orderId: { bsonType: 'string', description: 'orders.id from PostgreSQL (UUID as string)' },
                orderItemId: { bsonType: 'string' },
                eventId: { bsonType: 'string' },
                eventSnapshot: {
                    bsonType: 'object',
                    // Only the name is required. ORDER_CREATED carries the event's
                    // name but not its venue or date, and this service will not ask
                    // the Order Service for them - a synchronous call between
                    // services is exactly what the architecture forbids. Enriching
                    // the contract is the honest fix if a ticket ever needs to print
                    // the venue.
                    required: ['name'],
                    properties: {
                        name: { bsonType: 'string' },
                        venue: { bsonType: ['string', 'null'] },
                        city: { bsonType: ['string', 'null'] },
                        startsAt: { bsonType: ['date', 'null'] }
                    }
                },
                ticketCategory: {
                    bsonType: 'object',
                    required: ['id', 'name'],
                    properties: {
                        id: { bsonType: 'string' },
                        name: { bsonType: 'string' },
                        price: { bsonType: 'decimal' }
                    }
                },
                holder: {
                    bsonType: 'object',
                    required: ['customerId', 'email'],
                    properties: {
                        customerId: { bsonType: 'string' },
                        name: { bsonType: 'string' },
                        email: { bsonType: 'string' }
                    }
                },
                qrCodePayload: { bsonType: 'string' },
                status: { enum: ['ISSUED', 'USED', 'CANCELLED'] },
                issuedAt: { bsonType: 'date' },
                usedAt: { bsonType: ['date', 'null'] }
            }
        }
    },
    validationLevel: 'strict',
    validationAction: 'error'
});

notificationsDb.tickets.createIndex({ ticketCode: 1 }, { unique: true, name: 'ux_tickets_code' });
notificationsDb.tickets.createIndex({ orderId: 1 }, { name: 'ix_tickets_order' });
notificationsDb.tickets.createIndex({ 'holder.customerId': 1, issuedAt: -1 }, { name: 'ix_tickets_customer' });
notificationsDb.tickets.createIndex({ eventId: 1, status: 1 }, { name: 'ix_tickets_event_status' });

// -----------------------------------------------------------------------------
// notifications - the delivery log (e-mail today, SMS/push later).
// Kept flexible on purpose: each channel carries a different payload shape.
// -----------------------------------------------------------------------------
notificationsDb.createCollection('notifications', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['orderId', 'channel', 'recipient', 'type', 'status', 'createdAt'],
            additionalProperties: true,
            properties: {
                orderId: { bsonType: 'string' },
                customerId: { bsonType: 'string' },
                channel: { enum: ['EMAIL', 'SMS', 'PUSH'] },
                type: { enum: ['TICKET_ISSUED', 'PAYMENT_REJECTED', 'ORDER_CANCELLED'] },
                recipient: { bsonType: 'string' },
                subject: { bsonType: 'string' },
                body: { bsonType: 'string' },
                status: { enum: ['PENDING', 'SENT', 'FAILED'] },
                attempts: { bsonType: 'int', minimum: 0 },
                error: { bsonType: ['string', 'null'] },
                createdAt: { bsonType: 'date' },
                sentAt: { bsonType: ['date', 'null'] }
            }
        }
    },
    validationLevel: 'strict',
    validationAction: 'error'
});

notificationsDb.notifications.createIndex({ orderId: 1, createdAt: -1 }, { name: 'ix_notifications_order' });
notificationsDb.notifications.createIndex({ status: 1, createdAt: 1 }, { name: 'ix_notifications_pending' });

// -----------------------------------------------------------------------------
// order_snapshots - this service's own read model.
//
// PAGAMENTO_APROVADO says how much was charged, never what was bought. Instead of
// asking the Order Service - which would be a synchronous call between services,
// the one thing this architecture forbids - the Notification Service listens to
// ORDER_CREATED and keeps the little it needs to print a ticket.
//
// The _id is the orderId, so a redelivered ORDER_CREATED overwrites rather than
// accumulating.
// -----------------------------------------------------------------------------
notificationsDb.createCollection('order_snapshots', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['_id', 'customerEmail', 'items'],
            additionalProperties: true,
            properties: {
                _id: { bsonType: 'string', description: 'the orderId' },
                customerId: { bsonType: ['string', 'null'] },
                customerName: { bsonType: ['string', 'null'] },
                customerEmail: { bsonType: 'string' },
                eventId: { bsonType: ['string', 'null'] },
                eventName: { bsonType: ['string', 'null'] },
                items: {
                    bsonType: 'array',
                    items: {
                        bsonType: 'object',
                        required: ['ticketCategoryId', 'quantity'],
                        properties: {
                            ticketCategoryId: { bsonType: 'string' },
                            categoryName: { bsonType: ['string', 'null'] },
                            quantity: { bsonType: 'int', minimum: 1 }
                        }
                    }
                },
                receivedAt: { bsonType: 'date' }
            }
        }
    }
});

notificationsDb.order_snapshots.createIndex({ receivedAt: 1 }, { name: 'ix_order_snapshots_received' });

// -----------------------------------------------------------------------------
// processed_events - idempotent consumer, same role as the PostgreSQL inbox
// tables. Kafka is at-least-once, so a redelivered PAGAMENTO_APROVADO must not
// issue a second ticket.
//
// TTL of 30 days: after that a redelivery is no longer plausible and the
// collection stops growing forever.
// -----------------------------------------------------------------------------
notificationsDb.createCollection('processed_events', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['eventId', 'consumerGroup', 'topic', 'processedAt'],
            properties: {
                eventId: { bsonType: 'string' },
                consumerGroup: { bsonType: 'string' },
                topic: { bsonType: 'string' },
                processedAt: { bsonType: 'date' }
            }
        }
    }
});

notificationsDb.processed_events.createIndex(
    { eventId: 1, consumerGroup: 1 },
    { unique: true, name: 'ux_processed_events' }
);
notificationsDb.processed_events.createIndex(
    { processedAt: 1 },
    { expireAfterSeconds: 60 * 60 * 24 * 30, name: 'ttl_processed_events' }
);

print('[ticketflow] notification database initialised: '
    + notificationsDb.getCollectionNames().join(', '));
