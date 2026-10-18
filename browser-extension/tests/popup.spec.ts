// Loads the built extension into Chromium and checks the popup explains a missing JobFinder app.
// No native host is registered for Playwright's Chromium, so the worker must land in an error state.
import { chromium, expect, test, type BrowserContext } from '@playwright/test';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const extensionPath = resolve('dist-e2e');
/** Derived from the manifest `key`; the native host manifest allows this id. */
const DEV_EXTENSION_ID = 'cnleajglbkmfgfkfihheoacmojpklpop';
let context: BrowserContext;
let profileDir: string;

test.beforeAll(async () => {
  profileDir = mkdtempSync(join(tmpdir(), 'jobfinder-ext-'));
  context = await chromium.launchPersistentContext(profileDir, {
    channel: 'chromium', // The full Chromium build; the headless shell cannot load extensions.
    args: [`--disable-extensions-except=${extensionPath}`, `--load-extension=${extensionPath}`],
  });
});

test.afterAll(async () => {
  await context?.close();
  rmSync(profileDir, { recursive: true, force: true });
});

test('popup shows that the JobFinder app cannot be reached', async () => {
  const worker = context.serviceWorkers()[0] ?? (await context.waitForEvent('serviceworker'));
  const extensionId = new URL(worker.url()).host;
  expect(extensionId).toBe(DEV_EXTENSION_ID);

  const popup = await context.newPage();
  await popup.goto(`chrome-extension://${extensionId}/popup.html`);

  const status = popup.locator('#status');
  await expect(status).toHaveAttribute('data-state', /^(host_not_installed|app_not_running)$/);
  await expect(status).toContainText(/helper is not installed|app is not running/);
  await expect(popup.getByRole('button', { name: 'Try again' })).toBeVisible();
});
