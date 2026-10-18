// The small "Autofill" and "AI help" buttons shown under a form field in focus, and the panel for
// their results. Lives in a shadow root so the page's styles never reach it. Nothing goes into a
// field until the user presses Insert, and nothing is ever submitted.
import { describeAutofill, describeProblem } from '../shared/formText';
import { areFieldButtonsOn } from '../shared/settings';
import type { AutofillResult, DraftResult, UiRequest } from '../shared/ui-messages';
import { describeControl, fillControl, isControl, typeOf, type Control } from './fields';

/** Field types the buttons appear for. */
const TEXT_TYPES = new Set(['text', 'email', 'tel', 'url', 'number', 'date', 'textarea', 'select', 'radio']);

const STYLE = `
:host { all: initial; position: fixed; z-index: 2147483647; font: 13px/1.4 system-ui, 'Segoe UI', sans-serif; }
.box { color: #1b1b1f; background: #ffffff; border: 1px solid #c4c6d0; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,.18); padding: 4px; max-width: 360px; }
.row { display: flex; gap: 4px; }
button { font: inherit; border: none; border-radius: 999px; padding: 4px 10px; cursor: pointer; background: #dde1ff; color: #001257; }
button:hover { background: #c7cdfb; }
button:focus-visible { outline: 2px solid #3355cc; outline-offset: 1px; }
button.plain { background: transparent; color: #3355cc; }
.panel { padding: 6px 4px 2px; }
.panel p { margin: 0 0 6px; }
textarea { box-sizing: border-box; width: 100%; min-height: 72px; font: inherit; border: 1px solid #c4c6d0; border-radius: 8px; padding: 6px; resize: vertical; color: inherit; background: transparent; }
.error { color: #ba1a1a; }
@media (prefers-color-scheme: dark) {
  .box { color: #e4e1e6; background: #1b1b1f; border-color: #46464f; }
  button { background: #353d6b; color: #dde1ff; }
  button:hover { background: #424a7a; }
  button.plain { color: #b8c3ff; background: transparent; }
  textarea { border-color: #46464f; }
  .error { color: #ffb4ab; }
}`;

export class FieldButtons {
  private host: HTMLElement | null = null;
  private root: ShadowRoot | null = null;
  private field: Control | null = null;
  /** A draft or a message is showing: the box stays until the user closes it. */
  private pinned = false;
  private hideTimer: ReturnType<typeof setTimeout> | undefined;
  /** The field the box just handed the focus back to: no new box for that focus. */
  private returning: Control | null = null;

  attach(doc: Document): void {
    doc.addEventListener('focusin', (e) => void this.onFocus(e.target), true);
    doc.addEventListener('focusout', (e) => this.onBlur(e.target), true);
    doc.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && this.host) this.hide(true);
    });
    const view = doc.defaultView;
    view?.addEventListener('scroll', () => this.place(), true);
    view?.addEventListener('resize', () => this.place());
  }

  private async onFocus(target: EventTarget | null): Promise<void> {
    if (target === this.host || !isControl(target)) return;
    if (target === this.returning) {
      this.returning = null;
      return;
    }
    if (this.pinned && this.field === target) return;
    if (!TEXT_TYPES.has(typeOf(target)) || !describeControl(target)) return;
    if (!(await areFieldButtonsOn())) return;
    clearTimeout(this.hideTimer);
    this.pinned = false;
    this.field = target;
    this.show(target.ownerDocument);
    this.renderButtons();
    this.place();
  }

  private onBlur(target: EventTarget | null): void {
    if (target !== this.field || this.pinned) return;
    clearTimeout(this.hideTimer);
    // Long enough for a click on the box to land first.
    this.hideTimer = setTimeout(() => {
      if (!this.pinned && this.host?.ownerDocument.activeElement !== this.host) this.hide(false);
    }, 250);
  }

  private show(doc: Document): void {
    if (this.host && this.host.ownerDocument === doc && this.host.isConnected) return;
    this.host?.remove();
    const host = doc.createElement('jobfinder-helper');
    host.setAttribute('data-jobfinder', 'buttons');
    const root = host.attachShadow({ mode: 'open' });
    const style = doc.createElement('style');
    style.textContent = STYLE;
    root.append(style);
    // Keeps the focus in the field when a button is pressed (but lets the draft box take it).
    host.addEventListener('mousedown', (e) => {
      if (!(e.composedPath()[0] instanceof (doc.defaultView ?? window).HTMLTextAreaElement)) e.preventDefault();
    });
    (doc.body ?? doc.documentElement).append(host);
    this.host = host;
    this.root = root;
  }

  private hide(refocus: boolean): void {
    const field = this.field;
    this.host?.remove();
    this.host = null;
    this.root = null;
    this.pinned = false;
    if (refocus && field) {
      this.returning = field;
      field.focus();
    }
  }

  /** Under the field, or above it when there is no room below. */
  private place(): void {
    const host = this.host;
    const field = this.field;
    if (!host || !field) return;
    if (!field.isConnected) {
      this.hide(false);
      return;
    }
    const view = field.ownerDocument.defaultView;
    const rect = field.getBoundingClientRect();
    const below = rect.bottom + 4;
    const height = host.getBoundingClientRect().height || 36;
    const top = below + height > (view?.innerHeight ?? 0) && rect.top > height + 4 ? rect.top - height - 4 : below;
    host.style.top = `${Math.round(top)}px`;
    host.style.left = `${Math.round(Math.max(4, rect.left))}px`;
  }

  private box(): HTMLElement {
    const root = this.root!;
    root.querySelector('.box')?.remove();
    const box = root.ownerDocument.createElement('div');
    box.className = 'box';
    box.setAttribute('role', 'group');
    box.setAttribute('aria-label', 'JobFinder');
    root.append(box);
    return box;
  }

  private button(parent: HTMLElement, label: string, onClick: () => void, plain = false): HTMLButtonElement {
    const b = parent.ownerDocument.createElement('button');
    b.type = 'button';
    b.textContent = label;
    if (plain) b.className = 'plain';
    b.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      onClick();
    });
    parent.append(b);
    return b;
  }

  private renderButtons(): void {
    if (!this.root) return;
    const box = this.box();
    const row = box.ownerDocument.createElement('div');
    row.className = 'row';
    box.append(row);
    this.button(row, 'Autofill', () => void this.autofill());
    this.button(row, 'AI help', () => void this.draft());
  }

  private message(text: string, isError = false): void {
    if (!this.root) return;
    this.pinned = true;
    const box = this.box();
    const panel = box.ownerDocument.createElement('div');
    panel.className = 'panel';
    const p = box.ownerDocument.createElement('p');
    p.textContent = text;
    p.setAttribute('role', 'status');
    if (isError) p.className = 'error';
    panel.append(p);
    box.append(panel);
    this.button(panel, 'Close', () => this.hide(true), true);
    this.place();
  }

  private async autofill(): Promise<void> {
    this.message('Filling the form…');
    let result: AutofillResult;
    try {
      result = await chrome.runtime.sendMessage({ kind: 'autofill' } satisfies UiRequest);
    } catch {
      result = { problem: 'failed', filled: 0, kept: 0, failed: 0, unmatched: 0, files: [] };
    }
    this.message(describeAutofill(result), result.problem !== undefined);
  }

  private async draft(): Promise<void> {
    const field = this.field;
    const info = field ? describeControl(field) : undefined;
    if (!field || !info) return;
    this.message('Writing a draft… It can take a minute when the AI model has to load.');
    let result: DraftResult;
    try {
      const current = typeOf(field) === 'radio' || typeOf(field) === 'select' ? undefined : field.value || undefined;
      result = await chrome.runtime.sendMessage({ kind: 'draftAnswer', field: info, current } satisfies UiRequest);
    } catch {
      result = { problem: 'failed' };
    }
    if (this.field !== field || !this.root) return;
    if ('problem' in result) {
      this.message(describeProblem(result.problem), true);
      return;
    }
    this.showDraft(field, result.text);
  }

  /** The draft in an editable box; it goes into the field only on Insert. */
  private showDraft(field: Control, text: string): void {
    this.pinned = true;
    const box = this.box();
    const doc = box.ownerDocument;
    const panel = doc.createElement('div');
    panel.className = 'panel';
    const p = doc.createElement('p');
    p.textContent = 'AI draft. Edit it, then insert it.';
    const area = doc.createElement('textarea');
    area.value = text;
    area.setAttribute('aria-label', 'AI draft');
    const row = doc.createElement('div');
    row.className = 'row';
    panel.append(p, area, row);
    box.append(panel);
    this.button(row, 'Insert', () => {
      const value = area.value.trim();
      if (value) fillControl(field, value);
      this.hide(true);
    });
    this.button(row, 'Cancel', () => this.hide(true), true);
    this.place();
    area.focus();
  }
}
