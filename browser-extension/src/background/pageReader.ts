// The fallback reader: opens a URL in a background tab, extracts the page, closes the tab.
// Reads run one at a time so the user never gets a burst of tabs.
import { PROTOCOL_VERSION, READ_LIMITS } from '../shared/constants';
import { errorMsg, type ErrorCode, type ErrorMsg, type PageReadOut, type ReadPageIn } from '../shared/protocol';
import { extractPage, probeSelector } from './pageScripts';

class ReadError extends Error {
  constructor(
    readonly code: ErrorCode,
    message: string,
  ) {
    super(message);
  }
}

export class PageReader {
  private queue: Promise<void> = Promise.resolve();
  private queued = 0;
  private readonly tabs = new Set<number>();

  constructor(private readonly reply: (msg: PageReadOut | ErrorMsg) => void) {}

  /** True for tabs this reader opened, so other features can ignore them. */
  owns(tabId: number): boolean {
    return this.tabs.has(tabId);
  }

  enqueue(raw: Partial<ReadPageIn>): void {
    const id = typeof raw.id === 'string' && raw.id.length <= READ_LIMITS.idChars ? raw.id : undefined;
    if (id === undefined) {
      this.reply(errorMsg('bad_request', `readPage needs a string "id" of at most ${READ_LIMITS.idChars} characters.`));
      return;
    }
    if (this.queued >= READ_LIMITS.maxQueued) {
      this.reply(errorMsg('busy', `Too many reads waiting (limit ${READ_LIMITS.maxQueued}). Try again later.`, id));
      return;
    }
    this.queued++;
    this.queue = this.queue.then(async () => {
      this.queued--;
      this.reply(await this.read(id, raw));
    });
  }

  private async read(id: string, req: Partial<ReadPageIn>): Promise<PageReadOut | ErrorMsg> {
    const started = Date.now();
    let tabId: number | undefined;
    try {
      const url = parseUrl(req.url);
      const selector = parseSelector(req.waitForSelector);
      const deadline = started + clampTimeout(req.timeoutMs);
      if (!(await chrome.permissions.contains({ origins: [`${url.origin}/*`] }))) {
        throw new ReadError('no_permission', `No permission to read ${url.origin}. Turn on the fallback reader in the extension options.`);
      }

      const tab = await chrome.tabs.create({ url: url.href, active: false });
      if (tab.id === undefined) throw new ReadError('tab_failed', 'The browser did not open a tab.');
      tabId = tab.id;
      this.tabs.add(tabId);

      await waitForLoad(tabId, deadline);
      await sleep(Math.min(READ_LIMITS.settleMs, deadline - Date.now())); // Let page scripts render.
      if (selector) await waitForSelector(tabId, selector, deadline);

      const data = await beforeDeadline(runInTab(tabId, extractPage, [READ_LIMITS, req.includeHtml === true]), deadline);
      return { v: PROTOCOL_VERSION, type: 'pageRead', id, ...data, elapsedMs: Date.now() - started };
    } catch (e) {
      if (e instanceof ReadError) return errorMsg(e.code, e.message, id);
      return errorMsg('internal', e instanceof Error ? e.message : String(e), id);
    } finally {
      if (tabId !== undefined) {
        this.tabs.delete(tabId);
        await chrome.tabs.remove(tabId).catch(() => undefined);
      }
    }
  }
}

function parseUrl(value: unknown): URL {
  if (typeof value !== 'string' || value.length > READ_LIMITS.urlChars) {
    throw new ReadError('bad_request', `"url" must be a string of at most ${READ_LIMITS.urlChars} characters.`);
  }
  try {
    const url = new URL(value);
    if (url.protocol === 'https:' || url.protocol === 'http:') return url;
  } catch {
    // Falls through to the error below.
  }
  throw new ReadError('bad_request', '"url" must be an absolute http or https URL.');
}

function parseSelector(value: unknown): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value === 'string' && value.length > 0 && value.length <= READ_LIMITS.selectorChars) return value;
  throw new ReadError('bad_request', `"waitForSelector" must be a CSS selector of at most ${READ_LIMITS.selectorChars} characters.`);
}

function clampTimeout(value: unknown): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) return READ_LIMITS.defaultTimeoutMs;
  return Math.min(Math.max(value, READ_LIMITS.minTimeoutMs), READ_LIMITS.maxTimeoutMs);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, Math.max(0, ms)));
}

function beforeDeadline<T>(work: Promise<T>, deadline: number): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new ReadError('timeout', 'The page did not finish in time.')), deadline - Date.now());
    work.then(resolve, reject).finally(() => clearTimeout(timer));
  });
}

function waitForLoad(tabId: number, deadline: number): Promise<void> {
  const loaded = new Promise<void>((resolve, reject) => {
    const onUpdated = (id: number, info: chrome.tabs.OnUpdatedInfo, tab: chrome.tabs.Tab) => {
      // The first "complete" can belong to the initial about:blank document.
      if (id === tabId && info.status === 'complete' && tab.url !== 'about:blank') done(resolve);
    };
    const onRemoved = (id: number) => {
      if (id === tabId) done(() => reject(new ReadError('tab_failed', 'The tab was closed before the page loaded.')));
    };
    const done = (finish: () => void) => {
      chrome.tabs.onUpdated.removeListener(onUpdated);
      chrome.tabs.onRemoved.removeListener(onRemoved);
      finish();
    };
    chrome.tabs.onUpdated.addListener(onUpdated);
    chrome.tabs.onRemoved.addListener(onRemoved);
  });
  return beforeDeadline(loaded, deadline);
}

async function waitForSelector(tabId: number, selector: string, deadline: number): Promise<void> {
  while (Date.now() < deadline) {
    const result = await beforeDeadline(runInTab(tabId, probeSelector, [selector]), deadline);
    if (result === 'found') return;
    if (result === 'invalid') throw new ReadError('bad_request', `"waitForSelector" is not a valid CSS selector.`);
    await sleep(READ_LIMITS.selectorPollMs);
  }
  throw new ReadError('timeout', `"${selector}" did not appear before the timeout.`);
}

async function runInTab<Args extends unknown[], Result>(
  tabId: number,
  func: (...args: Args) => Result,
  args: Args,
): Promise<Awaited<Result>> {
  try {
    const [first] = await chrome.scripting.executeScript({ target: { tabId }, func, args });
    if (first === undefined) throw new Error('The page returned no result.');
    return first.result as Awaited<Result>;
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    // A redirect to a site without permission (for example a login page) lands here.
    if (/cannot access|permission/i.test(message)) {
      throw new ReadError('no_permission', `No permission to read the page it ended up on: ${message}`);
    }
    throw new ReadError('extract_failed', message);
  }
}
