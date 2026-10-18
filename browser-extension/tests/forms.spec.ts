// Autofill, answer saving and AI help on a local application form. The e2e build answers form
// requests from chrome.storage.local (src/background/e2eFakeHost.ts) instead of the JobFinder app.
import { chromium, expect, test, type BrowserContext, type Page, type Worker } from '@playwright/test';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const extensionPath = resolve('dist-e2e');
let context: BrowserContext;
let worker: Worker;
let server: Server;
let profileDir: string;
let pageUrl: string;

test.beforeAll(async () => {
  const html = readFileSync(resolve('tests/fixtures/apply.html'));
  server = createServer((_, res) => res.writeHead(200, { 'content-type': 'text/html' }).end(html));
  await new Promise<void>((done) => server.listen(0, '127.0.0.1', done));
  pageUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}/apply`;

  profileDir = mkdtempSync(join(tmpdir(), 'jobfinder-forms-'));
  context = await chromium.launchPersistentContext(profileDir, {
    channel: 'chromium',
    args: [`--disable-extensions-except=${extensionPath}`, `--load-extension=${extensionPath}`],
  });
  worker = context.serviceWorkers()[0] ?? (await context.waitForEvent('serviceworker'));
  await worker.evaluate(async () => {
    await chrome.storage.local.set({
      sites: ['127.0.0.1'],
      e2eFakeHost: {
        answers: {
          'First name': 'Ada',
          'Last name': 'Lovelace',
          Email: 'ada@example.com',
          Phone: '+44 20 7946 0000',
          'Years of experience': '9',
          Country: 'India',
          'Are you authorized to work in this country?': 'Yes',
          'Willing to relocate': 'Yes',
          City: 'Pune',
          // Never used: these fields are never sent to the app.
          'Create a password': 'hunter2',
          'Card number': '4111 1111 1111 1111',
        },
        draft: 'Your robots need offline-first apps; I build them.',
        resumeFileName: 'ada-resume.pdf',
      },
    });
  });
  // The content script follows the sites setting; wait until it is registered.
  await expect
    .poll(() => worker.evaluate(async () => (await chrome.scripting.getRegisteredContentScripts()).length))
    .toBe(1);
});

test.afterAll(async () => {
  await context?.close();
  server?.close();
  rmSync(profileDir, { recursive: true, force: true });
});

async function openForm(): Promise<Page> {
  const page = await context.newPage();
  await page.goto(pageUrl);
  await expect(page.locator('html')).toHaveAttribute('data-jobfinder-forms', 'on');
  return page;
}

test('Autofill fills the form from saved answers and never sends it', async () => {
  const page = await openForm();
  await page.locator('#first').focus();
  const helper = page.locator('jobfinder-helper');
  await helper.getByRole('button', { name: 'Autofill' }).click();
  await expect(helper.getByRole('status')).toContainText('Filled 9 fields');
  await expect(helper.getByRole('status')).toContainText('Attach your resume (ada-resume.pdf) to “Resume/CV” yourself');

  await expect(page.locator('#first')).toHaveValue('Ada');
  await expect(page.locator('#last')).toHaveValue('Lovelace');
  await expect(page.locator('#email')).toHaveValue('ada@example.com');
  await expect(page.locator('#phone')).toHaveValue('+44 20 7946 0000');
  // React-style input: the page saw the change.
  await expect(page.locator('#years')).toHaveValue('9');
  await expect(page.locator('#years-state')).toHaveText('9');
  await expect(page.locator('#country')).toHaveValue('in');
  await expect(page.locator('input[name="auth"][value="1"]')).toBeChecked();
  await expect(page.locator('#relocate')).toBeChecked();
  // The same-origin iframe.
  await expect(page.frameLocator('#ats').locator('#city')).toHaveValue('Pune');
  // Sensitive fields stay empty; the form was not sent.
  await expect(page.locator('#pw')).toHaveValue('');
  await expect(page.locator('#card')).toHaveValue('');
  expect(await page.evaluate(() => (window as unknown as { submitted: number }).submitted)).toBe(0);
  await page.close();
});

test('Sending the form saves the answers without passwords or card numbers', async () => {
  const page = await openForm();
  await page.locator('#first').fill('Grace');
  await page.locator('#email').fill('grace@example.com');
  await page.locator('#country').selectOption('us');
  await page.locator('input[name="auth"][value="0"]').check();
  await page.locator('#pw').fill('s3cret-pass');
  await page.locator('#card').fill('4111 1111 1111 1111');
  await page.frameLocator('#ats').locator('#city').fill('Boston');
  await page.getByRole('button', { name: 'Submit application' }).click();

  const saved = await expect
    .poll(() => worker.evaluate(async () => (await chrome.storage.local.get('e2eSaved')).e2eSaved ?? null))
    .not.toBeNull()
    .then(() => worker.evaluate(async () => (await chrome.storage.local.get('e2eSaved')).e2eSaved));
  const { url, fields } = saved as { url: string; fields: Array<{ label?: string; value?: string; inputType: string }> };
  expect(url).toBe(pageUrl);
  const byLabel = Object.fromEntries(fields.map((f) => [f.label, f.value]));
  expect(byLabel['First name']).toBe('Grace');
  expect(byLabel['Email']).toBe('grace@example.com');
  expect(byLabel['Country']).toBe('United States');
  expect(byLabel['Are you authorized to work in this country?']).toBe('No');
  const all = JSON.stringify(fields);
  expect(all).not.toContain('s3cret-pass');
  expect(all).not.toContain('4111');
  expect(fields.some((f) => f.inputType === 'password' || f.inputType === 'file')).toBe(false);
  await page.close();
});

test('AI help shows an editable draft that goes in only on Insert', async () => {
  const page = await openForm();
  await page.locator('#cover').focus();
  const helper = page.locator('jobfinder-helper');
  await helper.getByRole('button', { name: 'AI help' }).click();
  const draft = helper.getByRole('textbox', { name: 'AI draft' });
  await expect(draft).toHaveValue('Your robots need offline-first apps; I build them.');
  await expect(page.locator('#cover')).toHaveValue('');
  await draft.fill('I build offline-first Android apps, which your field robots need.');
  await helper.getByRole('button', { name: 'Insert' }).click();
  await expect(page.locator('#cover')).toHaveValue('I build offline-first Android apps, which your field robots need.');
  await expect(helper).toHaveCount(0);
  await page.close();
});
