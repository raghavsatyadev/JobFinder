// Form answers in the service worker: keeps the content script registered for the sites that are
// on, forwards saved answers to the app, and runs autofill and AI help drafts.
import { FORM_LIMITS } from '../shared/constants';
import type { FieldInfo } from '../shared/protocol';
import { isRecordingAnswers } from '../shared/settings';
import { getSites } from '../shared/settings';
import { isUrlWatched, originsFor } from '../shared/sites';
import type { AutofillResult, DraftResult, FormProblem } from '../shared/ui-messages';
import type { HostRequests, HostResult } from './hostRequests';

const SCRIPT_ID = 'jobfinder-forms';

interface FillOutcome {
  filled: number;
  kept: number;
  failed: number;
}

export class FormAssistant {
  constructor(private readonly host: HostRequests) {}

  /** Registers the content script for exactly the sites that are on (setting and permission). */
  async syncContentScript(): Promise<void> {
    const matches: string[] = [];
    for (const site of await getSites()) {
      const origins = originsFor(site);
      if (await chrome.permissions.contains({ origins })) matches.push(...origins);
    }
    const existing = await chrome.scripting.getRegisteredContentScripts({ ids: [SCRIPT_ID] });
    if (matches.length === 0) {
      if (existing.length > 0) await chrome.scripting.unregisterContentScripts({ ids: [SCRIPT_ID] });
      return;
    }
    const script: chrome.scripting.RegisteredContentScript = {
      id: SCRIPT_ID,
      matches,
      js: ['content.js'],
      allFrames: true,
      runAt: 'document_idle',
      persistAcrossSessions: true,
    };
    if (existing.length > 0) await chrome.scripting.updateContentScripts([script]);
    else await chrome.scripting.registerContentScripts([script]);
  }

  /** A form was sent in a frame of [tabUrl]; saves its answers when the site is on. */
  async saved(frameUrl: string | undefined, tabUrl: string | undefined, fields: FieldInfo[]): Promise<void> {
    const url = tabUrl ?? frameUrl;
    if (!url || !frameUrl || !(await isUrlWatched(frameUrl)) || !(await isRecordingAnswers())) return;
    const clean = fields.slice(0, FORM_LIMITS.maxFields).map(({ ref: _ref, ...rest }) => rest);
    if (clean.length === 0) return;
    await this.host.request('saveAnswers', { url, fields: clean }, FORM_LIMITS.saveTimeoutMs);
  }

  /** Collects the fields of every frame, asks the app for answers and fills the empty fields. */
  async autofill(tabId: number): Promise<AutofillResult> {
    const result: AutofillResult = { filled: 0, kept: 0, failed: 0, unmatched: 0, files: [] };
    const tab = await chrome.tabs.get(tabId).catch(() => undefined);
    if (!tab?.url) return { ...result, problem: 'no_permission' };
    try {
      await chrome.scripting.executeScript({ target: { tabId, allFrames: true }, files: ['content.js'] });
    } catch {
      return { ...result, problem: 'no_permission' };
    }
    const frames = await chrome.scripting.executeScript({
      target: { tabId, allFrames: true },
      func: () => globalThis.__jobfinderForms?.collect(false) ?? [],
    });
    const fields: FieldInfo[] = [];
    const frameOf = new Map<string, { frameId: number; ref: string }>();
    for (const frame of frames) {
      for (const field of (frame.result as FieldInfo[] | undefined) ?? []) {
        if (!field.ref) continue;
        const ref = `${frame.frameId}:${field.ref}`;
        frameOf.set(ref, { frameId: frame.frameId, ref: field.ref });
        if (field.inputType === 'file') result.files.push(field.label || field.name || 'Resume');
        else fields.push({ ...field, ref });
      }
    }
    if (fields.length === 0 && result.files.length === 0) return { ...result, problem: 'no_fields' };
    const reply = await this.host.request('answersFor', { url: tab.url, fields: fields.slice(0, FORM_LIMITS.maxFields), ai: true }, FORM_LIMITS.answersTimeoutMs);
    if (!reply.ok) return { ...result, problem: problemOf(reply) };
    result.resumeFileName = reply.reply.resumeFileName;
    const byFrame = new Map<number, Array<{ ref: string; value: string }>>();
    for (const answer of reply.reply.answers) {
      const target = frameOf.get(answer.ref);
      if (!target) continue;
      const list = byFrame.get(target.frameId) ?? [];
      list.push({ ref: target.ref, value: answer.value });
      byFrame.set(target.frameId, list);
    }
    for (const [frameId, fills] of byFrame) {
      const [done] = await chrome.scripting.executeScript({
        target: { tabId, frameIds: [frameId] },
        func: (list: Array<{ ref: string; value: string }>) => globalThis.__jobfinderForms?.fill(list) ?? { filled: 0, kept: 0, failed: 0 },
        args: [fills],
      });
      const outcome = done?.result as FillOutcome | undefined;
      if (!outcome) continue;
      result.filled += outcome.filled;
      result.kept += outcome.kept;
      result.failed += outcome.failed;
    }
    result.unmatched = Math.max(0, fields.length - reply.reply.answers.length);
    return result;
  }

  async draft(tabUrl: string | undefined, frameUrl: string | undefined, field: FieldInfo, current?: string): Promise<DraftResult> {
    const url = tabUrl ?? frameUrl;
    if (!url || !frameUrl || !(await isUrlWatched(frameUrl))) return { problem: 'no_permission' };
    const reply = await this.host.request('draftAnswer', { url, field, ...(current ? { current: current.slice(0, FORM_LIMITS.valueChars) } : {}) }, FORM_LIMITS.draftTimeoutMs);
    return reply.ok ? { text: reply.reply.text } : { problem: problemOf(reply) };
  }
}

function problemOf(reply: HostResult<unknown> & { ok: false }): FormProblem {
  switch (reply.code) {
    case 'not_connected':
    case 'timeout':
    case 'ai_failed':
      return reply.code;
    case 'no_permission':
      return 'no_permission';
    default:
      // The relay answers "internal" for everything while the app is closed.
      return /not running/i.test(reply.message) ? 'not_connected' : 'failed';
  }
}
