// Asks the app whether the page in a tab is a job it knows, and shows the answer on the toolbar icon.
import { PROTOCOL_VERSION } from '../shared/constants';
import { newId, type ErrorMsg, type JobIn } from '../shared/protocol';
import { isUrlWatched } from '../shared/sites';
import { clearTabJob, setTabJob } from '../shared/state';
import type { NativeLink } from './nativeLink';

export class JobLookup {
  /** Latest request per tab; an older reply for the same tab is ignored. */
  private readonly pending = new Map<number, { id: string; url: string }>();

  constructor(private readonly link: NativeLink) {}

  async lookup(tabId: number, url: string): Promise<void> {
    await this.forget(tabId);
    if (!(await isUrlWatched(url))) return;
    const id = newId();
    if (this.link.send({ v: PROTOCOL_VERSION, type: 'jobForUrl', id, url })) this.pending.set(tabId, { id, url });
  }

  async onReply(msg: JobIn | ErrorMsg): Promise<void> {
    const entry = [...this.pending].find(([, request]) => request.id === msg.id);
    if (!entry) return;
    const [tabId, { url }] = entry;
    this.pending.delete(tabId);
    if (msg.type === 'error') return;

    const { found, jobId, title, company, atsScore } = msg;
    await setTabJob(tabId, { url, found, jobId, title, company, atsScore });
    if (!found) return;
    const score = typeof atsScore === 'number' ? String(Math.round(atsScore)) : '';
    await chrome.action.setBadgeBackgroundColor({ tabId, color: '#2e7d32' }).catch(() => undefined);
    await chrome.action.setBadgeText({ tabId, text: score || '✓' }).catch(() => undefined);
    const label = [title, company].filter(Boolean).join(' at ');
    await chrome.action.setTitle({ tabId, title: `JobFinder: ${label || 'saved job'}` }).catch(() => undefined);
  }

  async forget(tabId: number): Promise<void> {
    this.pending.delete(tabId);
    await clearTabJob(tabId);
    // These reject once the tab is gone; nothing to reset then.
    await chrome.action.setBadgeText({ tabId, text: '' }).catch(() => undefined);
    await chrome.action.setTitle({ tabId, title: 'JobFinder' }).catch(() => undefined);
  }
}
