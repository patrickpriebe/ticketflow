/**
 * Regenerates the screenshots used in the README.
 *
 * Runs the real purchase flow against the local stack and writes PNGs to
 * `docs/img/`. Nothing is staged or mocked: the order it photographs is a real
 * order, it crosses Kafka, and the "paid" screenshot only appears because the
 * Payment Service actually settled it.
 *
 *   docker compose --profile apps up -d
 *   cd frontend && npm run dev
 *   node scripts/screenshots/capture.mjs
 *
 * Uses the Chrome already installed on the machine (`channel: 'chrome'`), so
 * there is no browser download and no extra 150 MB in the repository.
 *
 * The sign-in screen is captured from the deployed site, because that is the
 * only place where the Google button exists — locally the provider is not
 * configured and the screen falls back to the development form. Every other
 * screenshot comes from the local stack, where the token issuer works without a
 * password.
 */
import { chromium } from 'playwright-core';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const LOCAL = 'http://localhost:5173';
const DEPLOYED = 'https://ticketflow-br.vercel.app';
const GRAFANA = 'http://localhost:3002';

/**
 * `node capture.mjs grafana` captura só o dashboard.
 *
 * Separado porque o fluxo de compra cria um pedido de verdade a cada execução, e
 * repetir isso só para refazer uma imagem de painel seria sujar o banco à toa.
 */
const only = process.argv[2];

const here = dirname(fileURLToPath(import.meta.url));
const outDir = join(here, '..', '..', 'docs', 'img');

const VIEWPORT = { width: 1440, height: 900 };

/** Espera a rede sossegar e as fontes carregarem, senão o texto sai piscando. */
async function settle(page, ms = 900) {
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.evaluate(() => document.fonts?.ready).catch(() => {});
  await page.waitForTimeout(ms);
}

async function shot(page, name, { fullPage = false } = {}) {
  const file = join(outDir, `${name}.png`);
  await page.screenshot({ path: file, fullPage });
  console.log(`  ✓ ${name}.png`);
}

/**
 * O dashboard do Grafana.
 *
 * `kiosk` tira o menu lateral e o cabeçalho — numa imagem de README o que
 * interessa são os painéis, não a navegação da ferramenta. A janela é alta de
 * propósito: os onze painéis não cabem em 900px, e uma captura cortada no meio
 * de um gráfico é pior que nenhuma.
 */
async function captureGrafana(browser) {
  const context = await browser.newContext({
    viewport: { width: 1600, height: 2200 },
    deviceScaleFactor: 1,
    locale: 'pt-BR',
    colorScheme: 'light',
  });
  const page = await context.newPage();

  await page.goto(`${GRAFANA}/d/ticketflow-overview?from=now-1h&to=now&kiosk`,
    { waitUntil: 'domcontentloaded' });

  // Os painéis carregam por consulta, não com a página. Esperar o texto de
  // carregamento sumir é mais confiável que esperar um tempo fixo.
  await page.getByText('Loading plugin panel').first()
    .waitFor({ state: 'detached', timeout: 60000 }).catch(() => {});
  await page.waitForTimeout(6000);

  await shot(page, '11-grafana', { fullPage: true });
  await context.close();
}

async function main() {
  await mkdir(outDir, { recursive: true });

  const browser = await chromium.launch({ channel: 'chrome' });

  if (only === 'grafana') {
    console.log('Capturando o dashboard…');
    await captureGrafana(browser);
    await browser.close();
    console.log('\nPronto. Arquivo em docs/img/');
    return;
  }
  const context = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: 1,
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    colorScheme: 'dark',
  });
  const page = await context.newPage();

  console.log('Capturando o catálogo…');
  await page.goto(LOCAL, { waitUntil: 'domcontentloaded' });
  await settle(page);
  await shot(page, '01-home');

  await page.goto(`${LOCAL}/events`, { waitUntil: 'domcontentloaded' });
  await settle(page);
  await shot(page, '02-discover');

  console.log('Abrindo um evento e montando o carrinho…');
  // O primeiro cartão da grade, seja qual for o seed do dia.
  await page.locator('.event-card a').first().click();
  await page.waitForURL(/\/events\/[0-9a-f-]{36}/);
  await settle(page);
  await shot(page, '03-event');

  // Duas categorias, para o pedido ter mais de uma linha — é o que torna o
  // resumo do checkout interessante de olhar.
  const plus = page.locator('.qty button:last-child');
  await plus.nth(0).click();
  await plus.nth(0).click();
  await plus.nth(1).click();
  await page.waitForTimeout(300);
  await shot(page, '04-event-cart');

  console.log('Checkout…');
  await page.getByRole('button', { name: 'Ir para o pagamento' }).click();
  await page.waitForURL(/\/checkout/);
  await settle(page);
  await shot(page, '05-checkout');

  console.log('Identificação (emissor local)…');
  await page.getByRole('button', { name: /Entrar para concluir/ }).click();
  await page.waitForURL(/\/signin/);
  await settle(page);

  await page.locator('#signin-name').fill('Ana Souza');
  await page.locator('#signin-email').fill('ana.souza@example.com');
  await page.getByRole('button', { name: 'Entrar' }).click();

  // Volta sozinho para o checkout, com o carrinho intacto.
  await page.waitForURL(/\/checkout/, { timeout: 15000 });
  await settle(page);

  console.log('Confirmando o pedido…');
  await page.getByRole('button', { name: /Confirmar pedido/ }).click();
  await page.waitForURL(/\/orders\/[0-9a-f-]{36}/, { timeout: 20000 });

  // O instante que a premissa do projeto tenta demonstrar: o pedido já existe,
  // os ingressos já estão reservados, e o pagamento ainda não aconteceu.
  await page.waitForTimeout(600);
  await shot(page, '06-order-pending');

  console.log('Esperando o Kafka resolver o pagamento…');
  // Sem tempo fixo: espera o selo mudar. Se demorar, é sintoma, não flakiness.
  await page.getByText('Pago', { exact: true }).first()
    .waitFor({ state: 'visible', timeout: 90000 });
  await settle(page);
  await shot(page, '07-order-paid', { fullPage: true });

  console.log('Meus pedidos…');
  await page.goto(`${LOCAL}/orders`, { waitUntil: 'domcontentloaded' });
  await settle(page);
  await shot(page, '08-my-orders');

  console.log('Tema claro…');
  await context.close();
  const light = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: 1,
    locale: 'pt-BR',
    colorScheme: 'light',
  });
  const lightPage = await light.newPage();
  await lightPage.goto(`${LOCAL}/events`, { waitUntil: 'domcontentloaded' });
  await settle(lightPage);
  await shot(lightPage, '09-discover-light');
  await light.close();

  console.log('Tela de entrar, do ambiente publicado…');
  const deployed = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: 1,
    locale: 'pt-BR',
    colorScheme: 'dark',
  });
  const deployedPage = await deployed.newPage();
  await deployedPage.goto(`${DEPLOYED}/signin`, { waitUntil: 'domcontentloaded' });
  await settle(deployedPage, 2500);
  await shot(deployedPage, '10-signin');
  await deployed.close();

  console.log('Dashboard…');
  await captureGrafana(browser);

  await browser.close();
  console.log(`\nPronto. Arquivos em docs/img/`);
}

main().catch((error) => {
  console.error('\nFalhou:', error.message);
  process.exit(1);
});
