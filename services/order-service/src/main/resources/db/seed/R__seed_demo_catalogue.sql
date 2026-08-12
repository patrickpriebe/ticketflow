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
--
-- DO UPDATE rather than DO NOTHING: with DO NOTHING a fix to a name or a price
-- here never reached a database that had already been seeded, and the only way
-- out was `docker compose down -v`. The counters (reserved/sold) are the one
-- thing deliberately left alone - they belong to whoever has been buying.
-- =============================================================================

INSERT INTO events (id, name, description, venue, city, starts_at, sales_start_at, sales_end_at, status)
VALUES
    ('11111111-1111-4111-8111-111111111111',
     'Rock in Rio 2026 · Dia 1',
     'O Palco Mundo abre a edição de 2026 com quatro atrações internacionais e uma abertura nacional. Portões às 14h, primeira apresentação às 16h. O ingresso dá acesso a todos os palcos do dia.',
     'Parque Olímpico', 'Rio de Janeiro',
     '2026-09-18 18:00:00+00', '2026-04-01 13:00:00+00', '2026-09-18 12:00:00+00', 'ON_SALE'),

    ('22222222-2222-4222-8222-222222222222',
     'Sinfônica Municipal · Beethoven 9',
     'A Nona Sinfonia completa, com coral e solistas convidados, encerrando a temporada de assinatura. Regência do maestro titular. Duração aproximada de 70 minutos, sem intervalo.',
     'Theatro Municipal', 'São Paulo',
     '2026-11-05 23:00:00+00', '2026-06-01 13:00:00+00', '2026-11-05 20:00:00+00', 'ON_SALE'),

    ('33333333-3333-4333-8333-333333333333',
     'Final do Campeonato Estadual',
     'Jogo decisivo, ingressos limitados. Datas e horários ainda podem mudar por decisão da federação.',
     'Arena Central', 'Belo Horizonte',
     '2026-12-12 21:30:00+00', '2026-10-01 13:00:00+00', '2026-12-12 18:00:00+00', 'DRAFT'),

    ('44444444-4444-4444-8444-444444444444',
     'Jazz na Fábrica · Quinteto Noturno',
     'Uma noite de jazz autoral em formato intimista, com o quinteto apresentando o disco novo na íntegra. Casa com mesas, lotação reduzida e som acústico.',
     'Sesc Pompeia', 'São Paulo',
     '2026-09-05 23:30:00+00', '2026-05-10 13:00:00+00', '2026-09-05 21:00:00+00', 'ON_SALE'),

    ('55555555-5555-4555-8555-555555555555',
     'Stand-up · A Vida É Isso Aí',
     'Turnê nacional do espetáculo solo, com material inédito escrito no último ano. Classificação 16 anos. Não é permitido gravar durante a apresentação.',
     'Teatro Rival', 'Rio de Janeiro',
     '2026-08-29 23:00:00+00', '2026-06-15 13:00:00+00', '2026-08-29 20:00:00+00', 'ON_SALE'),

    ('66666666-6666-4666-8666-666666666666',
     'Clássico Regional · Semifinal',
     'Jogo de ida da semifinal, com torcida única. Abertura dos portões duas horas antes. Meia-entrada mediante comprovação na portaria.',
     'Arena do Sul', 'Porto Alegre',
     '2026-10-03 19:00:00+00', '2026-07-01 13:00:00+00', '2026-10-03 16:00:00+00', 'ON_SALE'),

    ('77777777-7777-4777-8777-777777777777',
     'Sunset Eletrônico · Edição Marina',
     'Doze horas de música eletrônica em dois palcos à beira da baía, do fim da tarde até o amanhecer. Line-up com nomes nacionais e um headliner internacional.',
     'Marina da Glória', 'Rio de Janeiro',
     '2026-10-17 21:00:00+00', '2026-05-20 13:00:00+00', '2026-10-17 18:00:00+00', 'ON_SALE'),

    ('88888888-8888-4888-8888-888888888888',
     'Turnê Nacional · Banda Litoral',
     'A banda leva para a estrada o repertório dos vinte anos de carreira, com participação de convidados em cada cidade. Show ao ar livre, sujeito a condições climáticas.',
     'Pedreira Paulo Leminski', 'Curitiba',
     '2026-11-21 22:00:00+00', '2026-07-15 13:00:00+00', '2026-11-21 19:00:00+00', 'ON_SALE'),

    ('99999999-9999-4999-8999-999999999999',
     'Ópera · A Flauta Mágica',
     'Montagem em dois atos com orquestra ao vivo e legendas em português. Uma sessão única, com pré-estreia aberta para assinantes.',
     'Teatro Castro Alves', 'Salvador',
     '2026-12-05 23:00:00+00', '2026-08-01 13:00:00+00', '2026-12-05 20:00:00+00', 'ON_SALE')

ON CONFLICT (id) DO UPDATE SET
    name           = EXCLUDED.name,
    description    = EXCLUDED.description,
    venue          = EXCLUDED.venue,
    city           = EXCLUDED.city,
    starts_at      = EXCLUDED.starts_at,
    sales_start_at = EXCLUDED.sales_start_at,
    sales_end_at   = EXCLUDED.sales_end_at,
    status         = EXCLUDED.status;


INSERT INTO ticket_categories (id, event_id, name, price_amount, currency, total_quantity)
VALUES
    ('aaaaaaaa-0001-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Pista',            650.00, 'BRL', 5000),
    ('aaaaaaaa-0001-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Pista Premium',   1200.00, 'BRL', 1200),
    ('aaaaaaaa-0001-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'Camarote',        2400.00, 'BRL',  300),

    ('bbbbbbbb-0002-4000-8000-000000000001', '22222222-2222-4222-8222-222222222222', 'Plateia',          180.00, 'BRL',  800),
    ('bbbbbbbb-0002-4000-8000-000000000002', '22222222-2222-4222-8222-222222222222', 'Balcão Nobre',     320.00, 'BRL',  240),

    ('cccccccc-0003-4000-8000-000000000001', '33333333-3333-4333-8333-333333333333', 'Arquibancada',      90.00, 'BRL', 20000),
    ('cccccccc-0003-4000-8000-000000000002', '33333333-3333-4333-8333-333333333333', 'Cadeira Coberta',  250.00, 'BRL',  4000),

    -- Casa pequena de propósito: é aqui que a tela mostra "últimos ingressos".
    ('dddddddd-0004-4000-8000-000000000001', '44444444-4444-4444-8444-444444444444', 'Mesa (2 lugares)', 240.00, 'BRL',   40),
    ('dddddddd-0004-4000-8000-000000000002', '44444444-4444-4444-8444-444444444444', 'Balcão em pé',      95.00, 'BRL',   60),

    ('eeeeeeee-0005-4000-8000-000000000001', '55555555-5555-4555-8555-555555555555', 'Plateia Baixa',    140.00, 'BRL',  300),
    ('eeeeeeee-0005-4000-8000-000000000002', '55555555-5555-4555-8555-555555555555', 'Plateia Alta',      80.00, 'BRL',  260),

    ('ffffffff-0006-4000-8000-000000000001', '66666666-6666-4666-8666-666666666666', 'Arquibancada',      40.00, 'BRL', 18000),
    ('ffffffff-0006-4000-8000-000000000002', '66666666-6666-4666-8666-666666666666', 'Cadeira Central',  180.00, 'BRL',  3000),
    ('ffffffff-0006-4000-8000-000000000003', '66666666-6666-4666-8666-666666666666', 'Camarote',         520.00, 'BRL',  180),

    ('a1a1a1a1-0007-4000-8000-000000000001', '77777777-7777-4777-8777-777777777777', 'Pista',            390.00, 'BRL', 6000),
    ('a1a1a1a1-0007-4000-8000-000000000002', '77777777-7777-4777-8777-777777777777', 'Lounge Deck',      890.00, 'BRL',  400),

    ('b2b2b2b2-0008-4000-8000-000000000001', '88888888-8888-4888-8888-888888888888', 'Pista',            220.00, 'BRL', 8000),
    ('b2b2b2b2-0008-4000-8000-000000000002', '88888888-8888-4888-8888-888888888888', 'Pista Premium',    440.00, 'BRL', 1500),

    ('c3c3c3c3-0009-4000-8000-000000000001', '99999999-9999-4999-8999-999999999999', 'Plateia',          210.00, 'BRL',  900),
    ('c3c3c3c3-0009-4000-8000-000000000002', '99999999-9999-4999-8999-999999999999', 'Frisa',            460.00, 'BRL',  120)

ON CONFLICT (id) DO UPDATE SET
    name         = EXCLUDED.name,
    price_amount = EXCLUDED.price_amount,
    currency     = EXCLUDED.currency;
