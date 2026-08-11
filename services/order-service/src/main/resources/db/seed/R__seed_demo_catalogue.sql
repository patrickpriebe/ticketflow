-- =============================================================================
-- Repeatable seed - DEMO DATA ONLY.
--
-- This file lives in db/seed, which is NOT part of the default Flyway
-- locations. It is applied only when the `dev` profile (or the local
-- docker-compose flyway container) adds classpath:db/seed to the locations.
-- Production never sees it.
--
-- Being a repeatable migration (R__), it re-runs whenever its checksum
-- changes, so every statement must be idempotent.
-- =============================================================================

INSERT INTO events (id, name, description, venue, city, starts_at, sales_start_at, sales_end_at, status)
VALUES
    ('11111111-1111-4111-8111-111111111111',
     'Rock in Rio 2026 - Dia 1',
     'Palco Mundo com line-up internacional.',
     'Parque Olimpico', 'Rio de Janeiro',
     '2026-09-18 18:00:00+00', '2026-04-01 13:00:00+00', '2026-09-18 12:00:00+00', 'ON_SALE'),

    ('22222222-2222-4222-8222-222222222222',
     'Sinfonica Municipal - Beethoven 9',
     'Nona sinfonia completa com coral.',
     'Theatro Municipal', 'Sao Paulo',
     '2026-11-05 23:00:00+00', '2026-06-01 13:00:00+00', '2026-11-05 20:00:00+00', 'ON_SALE'),

    ('33333333-3333-4333-8333-333333333333',
     'Final do Campeonato Estadual',
     'Jogo decisivo, ingressos limitados.',
     'Arena Central', 'Belo Horizonte',
     '2026-12-12 21:30:00+00', '2026-10-01 13:00:00+00', '2026-12-12 18:00:00+00', 'DRAFT')
ON CONFLICT (id) DO NOTHING;


INSERT INTO ticket_categories (id, event_id, name, price_amount, currency, total_quantity)
VALUES
    ('aaaaaaaa-0001-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Pista',           650.00, 'BRL', 5000),
    ('aaaaaaaa-0001-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Pista Premium',  1200.00, 'BRL', 1200),
    ('aaaaaaaa-0001-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'Camarote',       2400.00, 'BRL',  300),

    ('bbbbbbbb-0002-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Plateia',         180.00, 'BRL',  800),
    ('bbbbbbbb-0002-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222', 'Balcao Nobre',    320.00, 'BRL',  240),

    ('cccccccc-0003-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'Arquibancada',     90.00, 'BRL', 20000),
    ('cccccccc-0003-4000-8000-000000000002', '33333333-3333-4333-8333-333333333333', 'Cadeira Coberta', 250.00, 'BRL',  4000)
ON CONFLICT (id) DO NOTHING;
