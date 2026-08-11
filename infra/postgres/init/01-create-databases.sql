-- =============================================================================
-- Creates one database per microservice.
--
-- Database-per-service is the point here: Order Service and Payment Service
-- share a PostgreSQL *container* (local convenience) but never a schema.
-- Neither can read the other's tables, which is what forces all communication
-- through Kafka.
--
-- Runs once on a fresh volume. To re-run: docker compose down -v
-- =============================================================================

CREATE DATABASE ticketflow_orders;
CREATE DATABASE ticketflow_payments;

COMMENT ON DATABASE ticketflow_orders   IS 'Order Service - catalogue, orders, outbox';
COMMENT ON DATABASE ticketflow_payments IS 'Payment Service - payments, gateway attempts, outbox';
