// Requests from the popup, options page and content script to the service worker
// (chrome.runtime.sendMessage).
import type { FieldInfo } from './protocol';
import type { ConnectionInfo } from './state';

/** Why autofill or a draft did not run. */
export type FormProblem = 'not_connected' | 'no_fields' | 'no_permission' | 'timeout' | 'ai_failed' | 'failed';

export interface AutofillResult {
  problem?: FormProblem;
  filled: number;
  /** Fields the user had already filled. */
  kept: number;
  /** Values that fit no choice of the field. */
  failed: number;
  /** Fields without a saved answer. */
  unmatched: number;
  /** The labels of file fields: the user attaches the resume there. */
  files: string[];
  resumeFileName?: string;
}

export type DraftResult = { text: string } | { problem: FormProblem };

export type UiRequest =
  | { kind: 'reconnect' }
  | { kind: 'lookupTab'; tabId: number; url: string }
  /** From the popup: fill the forms of a tab. */
  | { kind: 'autofillTab'; tabId: number }
  /** From a content script: fill the forms of the sender's tab. */
  | { kind: 'autofill' }
  /** From a content script: a form was sent with these fields and values. */
  | { kind: 'formSubmitted'; fields: FieldInfo[] }
  /** From a content script: draft an answer for one field. */
  | { kind: 'draftAnswer'; field: FieldInfo; current?: string };

export function reconnect(): Promise<ConnectionInfo> {
  return chrome.runtime.sendMessage({ kind: 'reconnect' } satisfies UiRequest);
}

export function lookupTab(tabId: number, url: string): Promise<void> {
  return chrome.runtime.sendMessage({ kind: 'lookupTab', tabId, url } satisfies UiRequest);
}

export function autofillTab(tabId: number): Promise<AutofillResult> {
  return chrome.runtime.sendMessage({ kind: 'autofillTab', tabId } satisfies UiRequest);
}
