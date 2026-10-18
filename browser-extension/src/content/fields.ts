// Finds the fields of application forms in this frame's documents and fills them. Never touches a
// sensitive field (shared/sensitive.ts), never submits.
import { FORM_LIMITS } from '../shared/constants';
import type { FieldInfo } from '../shared/protocol';
import { isSensitive, normalise } from '../shared/sensitive';

export type Control = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement;

/** A field: one control, or every radio of a group. */
interface Field {
  info: FieldInfo;
  controls: Control[];
}

export interface Fill {
  ref: string;
  value: string;
}

export interface FillResult {
  filled: number;
  /** Fields the user had already filled; autofill leaves them. */
  kept: number;
  /** Values that fit no choice of a select or radio group, or no date or number field. */
  failed: number;
}

/** Input types that never hold an answer. */
const SKIPPED_TYPES = new Set(['hidden', 'submit', 'button', 'image', 'reset', 'search', 'password', 'color', 'range']);

const TRUTHY = /^(yes|y|true|1|on|checked|i agree|agree|accept)$/;

/** The fields of the last collect, by ref; refs are unique within this frame. */
let byRef = new Map<string, Field>();
let nextRef = 0;

/** Every answerable field in [docs]. With [withValues], each carries its current value. */
export function collectFields(docs: Document[], withValues: boolean, scope?: ParentNode): FieldInfo[] {
  const fields: Field[] = [];
  const groups = new Map<string, Field>();
  for (const doc of docs) {
    const root = scope && scope.ownerDocument === doc ? scope : doc;
    for (const el of root.querySelectorAll<Control>('input, select, textarea')) {
      const type = typeOf(el);
      if (SKIPPED_TYPES.has(type) || el.disabled || !isShown(el)) continue;
      if (type === 'radio') {
        const input = el as HTMLInputElement;
        const key = `${input.form ? formIndex(input.form) : 'doc'}|${input.name || refFor(input)}`;
        let group = groups.get(key);
        if (!group) {
          group = { info: describe(input, 'radio', groupLabel(input)), controls: [] };
          groups.set(key, group);
          fields.push(group);
        }
        group.controls.push(input);
        continue;
      }
      fields.push({ info: describe(el, type), controls: [el] });
    }
  }
  const out: FieldInfo[] = [];
  byRef = new Map();
  for (const field of fields) {
    if (field.info.inputType === 'radio') field.info.options = field.controls.map(choiceLabel).slice(0, FORM_LIMITS.maxOptions);
    const value = valueOf(field);
    // The value takes part in the check (a card number typed into an unlabelled box).
    if (isSensitive({ ...field.info, value })) continue;
    const ref = `f${++nextRef}`;
    byRef.set(ref, field);
    out.push({ ...field.info, ref, ...(withValues ? { value } : {}) });
    if (out.length >= FORM_LIMITS.maxFields) break;
  }
  return out;
}

/** Fills each ref of the last collect with its value, only where the user left the field empty. */
export function fillFields(fills: Fill[]): FillResult {
  const result: FillResult = { filled: 0, kept: 0, failed: 0 };
  for (const { ref, value } of fills) {
    const field = byRef.get(ref);
    if (!field || !value) continue;
    const outcome = fillField(field, value);
    result[outcome]++;
  }
  return result;
}

/** Puts [value] in one control; used by "AI help" after the user edited the draft. */
export function fillControl(el: Control, value: string): boolean {
  const type = typeOf(el);
  if (type === 'radio') {
    const input = el as HTMLInputElement;
    const doc = input.ownerDocument;
    const group = input.name
      ? [...doc.querySelectorAll<HTMLInputElement>(`input[type="radio"][name="${CSS.escape(input.name)}"]`)]
      : [input];
    return fillField({ info: describe(input, 'radio'), controls: group }, value, true) === 'filled';
  }
  return fillField({ info: describe(el, type), controls: [el] }, value, true) === 'filled';
}

/** The field info of one control, for "AI help". Undefined for a sensitive or unanswerable one. */
export function describeControl(el: Control): FieldInfo | undefined {
  const type = typeOf(el);
  if (SKIPPED_TYPES.has(type) || type === 'file') return undefined;
  const info = type === 'radio' ? describe(el as HTMLInputElement, 'radio', groupLabel(el as HTMLInputElement)) : describe(el, type);
  if (type === 'radio') {
    const doc = el.ownerDocument;
    const name = (el as HTMLInputElement).name;
    const group = name ? [...doc.querySelectorAll<HTMLInputElement>(`input[type="radio"][name="${CSS.escape(name)}"]`)] : [el];
    info.options = group.map(choiceLabel);
  }
  return isSensitive({ ...info, value: el.value }) ? undefined : info;
}

export function isControl(el: unknown): el is Control {
  // Instances from same-origin iframes have their own constructors, so test the tag name.
  const tag = (el as Element | null)?.tagName;
  return tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA';
}

export function typeOf(el: Control): string {
  if (el.tagName === 'SELECT') return 'select';
  if (el.tagName === 'TEXTAREA') return 'textarea';
  return ((el as HTMLInputElement).type || 'text').toLowerCase();
}

function fillField(field: Field, value: string, overwrite = false): keyof FillResult {
  const [first] = field.controls;
  if (!first) return 'failed';
  const type = typeOf(first);
  if (type === 'file') return 'failed';
  if (type === 'radio') {
    if (!overwrite && field.controls.some((r) => (r as HTMLInputElement).checked)) return 'kept';
    const target = bestChoice(field.controls, value, choiceLabel);
    if (!target) return 'failed';
    (target as HTMLInputElement).click();
    return 'filled';
  }
  if (type === 'checkbox') {
    const box = first as HTMLInputElement;
    const want = TRUTHY.test(normalise(value));
    // Only ticks a box; one the page ticked (or the user) is left as it is.
    if (!want || box.checked) return 'kept';
    box.click();
    return 'filled';
  }
  if (type === 'select') {
    const select = first as HTMLSelectElement;
    if (!overwrite && select.selectedIndex > 0 && select.value !== '') return 'kept';
    const option = bestChoice([...select.options].filter((o) => !o.disabled), value, (o) => o.text, (o) => o.value);
    if (!option) return 'failed';
    setValue(select, option.value);
    return 'filled';
  }
  const input = first as HTMLInputElement | HTMLTextAreaElement;
  if (!overwrite && input.value.trim() !== '') return 'kept';
  const text = valueFor(type, value);
  if (text === undefined) return 'failed';
  setValue(input, text);
  return 'filled';
}

/**
 * Sets a value the way typing does, so React and other frameworks see it: the prototype's own
 * setter (React replaces the element's), then input and change events.
 */
function setValue(el: Control, value: string): void {
  const view = el.ownerDocument.defaultView ?? window;
  let proto: object | null = Object.getPrototypeOf(el);
  let setter: ((v: string) => void) | undefined;
  while (proto && !setter) {
    setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
    proto = Object.getPrototypeOf(proto);
  }
  if (setter) setter.call(el, value);
  else el.value = value;
  el.dispatchEvent(new view.Event('input', { bubbles: true }));
  el.dispatchEvent(new view.Event('change', { bubbles: true }));
}

/** The text a field of [type] takes, or undefined when [value] does not fit it. */
function valueFor(type: string, value: string): string | undefined {
  if (type === 'number') {
    const n = value.replace(/,/g, '').match(/-?\d+(\.\d+)?/);
    return n ? n[0] : undefined;
  }
  if (type === 'date') {
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
    const t = Date.parse(value);
    return Number.isNaN(t) ? undefined : new Date(t).toISOString().slice(0, 10);
  }
  if (type === 'email') return value.includes('@') ? value.trim() : undefined;
  return value;
}

/** The choice whose text says [value]: same words, then its value attribute, then most shared words. */
function bestChoice<T>(choices: T[], value: string, text: (c: T) => string, raw?: (c: T) => string): T | undefined {
  const want = normalise(value);
  if (!want) return undefined;
  const yesNo = TRUTHY.test(want) ? 'yes' : /^(no|n|false|0|off)$/.test(want) ? 'no' : undefined;
  let best: T | undefined;
  let bestScore = 0;
  for (const choice of choices) {
    const label = normalise(text(choice));
    if (!label) continue;
    if (label === want || (raw && normalise(raw(choice)) === want)) return choice;
    if (yesNo && (label === yesNo || label.startsWith(`${yesNo} `))) return choice;
    const score = overlap(label, want);
    if (score > bestScore) {
      best = choice;
      bestScore = score;
    }
  }
  return bestScore >= 0.5 ? best : undefined;
}

function overlap(a: string, b: string): number {
  const wa = new Set(a.split(' '));
  const wb = new Set(b.split(' '));
  let shared = 0;
  wa.forEach((w) => wb.has(w) && shared++);
  return (2 * shared) / (wa.size + wb.size);
}

function valueOf(field: Field): string {
  const [first] = field.controls;
  if (!first) return '';
  const type = typeOf(first);
  if (type === 'radio') {
    const checked = field.controls.find((r) => (r as HTMLInputElement).checked);
    return checked ? choiceLabel(checked) : '';
  }
  if (type === 'checkbox') return (first as HTMLInputElement).checked ? 'Yes' : 'No';
  if (type === 'select') {
    const select = first as HTMLSelectElement;
    return [...select.selectedOptions]
      .filter((o) => o.value !== '' || select.selectedIndex > 0)
      .map((o) => o.text.trim())
      .join(', ');
  }
  if (type === 'file') return '';
  return (first as HTMLInputElement).value.slice(0, FORM_LIMITS.valueChars);
}

function describe(el: Control, type: string, label = labelOf(el)): FieldInfo {
  const info: FieldInfo = { inputType: type };
  const set = (key: 'label' | 'name' | 'elementId' | 'placeholder' | 'ariaLabel' | 'autocomplete', v: string | null | undefined) => {
    const t = v?.replace(/\s+/g, ' ').trim();
    if (t) info[key] = t.slice(0, FORM_LIMITS.textChars);
  };
  set('label', label);
  set('name', el.getAttribute('name'));
  set('elementId', el.id);
  set('placeholder', el.getAttribute('placeholder'));
  set('ariaLabel', el.getAttribute('aria-label'));
  set('autocomplete', el.getAttribute('autocomplete'));
  if (type === 'select') {
    info.options = [...(el as HTMLSelectElement).options]
      .map((o) => o.text.trim().slice(0, FORM_LIMITS.optionChars))
      .filter(Boolean)
      .slice(0, FORM_LIMITS.maxOptions);
  }
  return info;
}

/** The question a control answers: its label, aria-labelledby, or the text just before it. */
function labelOf(el: Control): string | undefined {
  const fromLabel = [...(el.labels ?? [])].map(textOf).find(Boolean);
  if (fromLabel) return fromLabel;
  const byIds = idsText(el, 'aria-labelledby');
  if (byIds) return byIds;
  return nearbyText(el);
}

/** A radio group's question: legend, group aria label, or the text before the group. */
function groupLabel(el: HTMLInputElement): string | undefined {
  const group = el.closest('[role="radiogroup"], [role="group"]');
  if (group) {
    const named = group.getAttribute('aria-label') || idsText(group, 'aria-labelledby');
    if (named) return named;
  }
  const legend = el.closest('fieldset')?.querySelector('legend');
  if (legend) {
    const t = textOf(legend);
    if (t) return t;
  }
  return nearbyText(group ?? el.closest('fieldset') ?? el.parentElement ?? el, true);
}

function choiceLabel(el: Control): string {
  return [...(el.labels ?? [])].map(textOf).find(Boolean) || el.getAttribute('aria-label') || (el as HTMLInputElement).value || '';
}

function idsText(el: Element, attr: string): string | undefined {
  const ids = el.getAttribute(attr)?.split(/\s+/).filter(Boolean) ?? [];
  const text = ids
    .map((id) => el.ownerDocument.getElementById(id))
    .map((n) => (n ? textOf(n) : ''))
    .filter(Boolean)
    .join(' ');
  return text || undefined;
}

/** Text of the closest earlier sibling of [el] or its parents, up to three levels. */
function nearbyText(el: Element, skipSelf = false): string | undefined {
  let node: Element | null = el;
  for (let level = 0; node && level < 3; level++, node = node.parentElement) {
    if (level === 0 && skipSelf) continue;
    for (let sib = node.previousElementSibling; sib; sib = sib.previousElementSibling) {
      if (sib.querySelector('input, select, textarea')) break;
      const t = textOf(sib);
      if (t) return t.length <= 200 ? t : undefined;
    }
  }
  return undefined;
}

/** Visible text of [node] without the text of controls and options inside it. */
function textOf(node: Node): string {
  let out = '';
  const walk = (n: Node) => {
    if (n.nodeType === Node.TEXT_NODE) out += n.textContent ?? '';
    else if (n.nodeType === Node.ELEMENT_NODE) {
      const tag = (n as Element).tagName;
      if (tag === 'SELECT' || tag === 'TEXTAREA' || tag === 'INPUT' || tag === 'SCRIPT' || tag === 'STYLE' || tag === 'OPTION') return;
      n.childNodes.forEach(walk);
    }
  };
  walk(node);
  return out.replace(/\s+/g, ' ').replace(/\s*\*\s*$/, '').trim();
}

function isShown(el: Element): boolean {
  const view = el.ownerDocument.defaultView;
  if (!view) return false;
  const style = view.getComputedStyle(el);
  if (style.display === 'none' || style.visibility === 'hidden') return false;
  // Styled radios and checkboxes are often 0×0 or transparent but still answer the question.
  return el.getClientRects().length > 0 || el.closest('label') !== null;
}

const formIds = new WeakMap<HTMLFormElement, number>();
let nextForm = 0;
function formIndex(form: HTMLFormElement): number {
  let id = formIds.get(form);
  if (id === undefined) formIds.set(form, (id = ++nextForm));
  return id;
}

const ownRefs = new WeakMap<Element, string>();
function refFor(el: Element): string {
  let ref = ownRefs.get(el);
  if (!ref) ownRefs.set(el, (ref = `r${++nextRef}`));
  return ref;
}
