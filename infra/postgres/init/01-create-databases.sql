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

-- Scratch databases for the integration tests.
--
-- They exist because Testcontainers cannot reach the Docker Engine API from a JVM
-- on some setups (Rancher Desktop here), and the tests accept
-- -Dticketflow.it.datasource.url to run against an existing database instead.
-- Creating them here means a fresh clone can run the whole suite without a manual
-- step.
--
-- WIPED BY EVERY TEST RUN. Never point anything else at them.
CREATE DATABASE ticketflow_orders_it;
CREATE DATABASE ticketflow_payments_it;

COMMENT ON DATABASE ticketflow_orders_it   IS 'Scratch database for Order Service integration tests - contents are disposable';
COMMENT ON DATABASE ticketflow_payments_it IS 'Scratch database for Payment Service integration tests - contents are disposable';
