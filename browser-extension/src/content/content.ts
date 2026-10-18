// Content script on the sites the user turned on (and injected on demand for autofill). Saves the
// answers when a form is sent, offers Autofill and AI help next to a field, and lets the service
// worker collect and fill fields through `globalThis.__jobfinderForms`.
//
// Each document is handled once: by its own frame's script, or by its parent's when the parent
// can reach it (same-origin ATS iframes, including srcdoc frames no script runs in). The
// `data-jobfinder-forms` mark on <html> says it is taken.
import type { FieldInfo } from '../shared/protocol';
import { isRecordingAnswers } from '../shared/settings';
import type { UiRequest } from '../shared/ui-messages';
import { FieldButtons } from './chip';
import { collectFields, fillFields, type Fill, type FillResult } from './fields';

interface FormsApi {
  collect(withValues: boolean): FieldInfo[];
  fill(fills: Fill[]): FillResult;
}

declare global {
  var __jobfinderForms: FormsApi | undefined;
}

/** Words on a button that sends a form or a step of it. */
const SEND_WORDS = /\b(submit|apply|send|next|continue|finish|review)\b/i;
const MARK = 'jobfinderForms';
/** A click and the submit it causes save once. */
const DEBOUNCE_MS = 1_500;

const owned: Document[] = [];
const buttons = new FieldButtons();
let lastSave = 0;

if (!globalThis.__jobfinderForms) {
  globalThis.__jobfinderForms = {
    collect: (withValues) => collectFields(liveDocs(), withValues),
    fill: fillFields,
  };
  if (adopt(document)) scanFrames(document);
}

function liveDocs(): Document[] {
  return owned.filter((doc) => doc.defaultView !== null);
}

/** Takes [doc] unless another script has; returns whether it did. */
function adopt(doc: Document): boolean {
  const root = doc.documentElement;
  if (!root || root.dataset[MARK]) return false;
  root.dataset[MARK] = 'on';
  owned.push(doc);
  doc.addEventListener('submit', (e) => onSubmit(e), true);
  doc.addEventListener('click', (e) => onClick(e), true);
  // `load` does not bubble, but a capturing listener sees an iframe's.
  doc.addEventListener('load', (e) => tryFrame(e.target), true);
  buttons.attach(doc);
  return true;
}

function scanFrames(doc: Document): void {
  doc.querySelectorAll('iframe, frame').forEach(tryFrame);
}

function tryFrame(target: EventTarget | null): void {
  const tag = (target as Element | null)?.tagName;
  if (tag !== 'IFRAME' && tag !== 'FRAME') return;
  try {
    const doc = (target as HTMLIFrameElement).contentDocument;
    if (doc && adopt(doc)) scanFrames(doc);
  } catch {
    // Another origin: its own script handles it, if the site is on.
  }
}

function onSubmit(e: Event): void {
  const form = e.target as HTMLFormElement;
  void save(form.ownerDocument, form, 1);
}

function onClick(e: Event): void {
  const target = e.target as Element | null;
  const button = target?.closest?.('button, input[type="submit"], input[type="button"], [role="button"]');
  if (!button) return;
  const text = [button.textContent, button.getAttribute('value'), button.getAttribute('aria-label')].join(' ');
  const isSubmit = (button as HTMLButtonElement).type === 'submit';
  if (!isSubmit && !SEND_WORDS.test(text)) return;
  const form = (button as HTMLButtonElement).form ?? button.closest('form');
  // Without a form, a lone search box is not an application: ask for two answers.
  void save(button.ownerDocument, form ?? undefined, form ? 1 : 2);
}

async function save(doc: Document, form: HTMLFormElement | undefined, minFields: number): Promise<void> {
  const now = Date.now();
  if (now - lastSave < DEBOUNCE_MS) return;
  // Read the values now, before the page clears or leaves the form.
  const docs = form ? [doc] : liveDocs();
  const fields = collectFields(docs, true, form).filter((f) => f.value && f.inputType !== 'file');
  if (fields.length < minFields) return;
  lastSave = now;
  if (!(await isRecordingAnswers())) return;
  const sent = fields.map(({ ref: _ref, ...rest }) => rest);
  chrome.runtime.sendMessage({ kind: 'formSubmitted', fields: sent } satisfies UiRequest).catch(() => undefined);
}
