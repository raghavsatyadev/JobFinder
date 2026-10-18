// Playwright only (`__E2E__`): answers form requests from chrome.storage.local instead of the app,
// so the e2e test can run autofill, saving and AI help without a native host. The production
// build drops this code (esbuild defines __E2E__ as false).
import { normalise } from '../shared/sensitive';
import type { AnswersIn, AnswersSavedIn, DraftIn, FieldInfo } from '../shared/protocol';

interface FakeHost {
  /** Values by normalised label or name. */
  answers?: Record<string, string>;
  draft?: string;
  resumeFileName?: string;
}

export async function fakeHostReply(type: string, id: string, body: Record<string, unknown>): Promise<AnswersIn | AnswersSavedIn | DraftIn | undefined> {
  if (!__E2E__) return undefined;
  const got = await chrome.storage.local.get('e2eFakeHost');
  const fake = got.e2eFakeHost as FakeHost | undefined;
  if (!fake) return undefined;
  const fields = (body.fields as FieldInfo[] | undefined) ?? [];
  if (type === 'saveAnswers') {
    await chrome.storage.local.set({ e2eSaved: body });
    return { v: 1, type: 'answersSaved', id, saved: fields.length };
  }
  if (type === 'answersFor') {
    const table = new Map(Object.entries(fake.answers ?? {}).map(([k, v]) => [normalise(k), v]));
    const answers = fields.flatMap((f) => {
      const value = [f.label, f.name, f.ariaLabel, f.placeholder].filter((s): s is string => !!s).map((s) => table.get(normalise(s))).find((v) => v !== undefined);
      return value !== undefined && f.ref ? [{ ref: f.ref, value, questionKey: normalise(f.label ?? f.name ?? ''), match: 'exact' as const, score: 1 }] : [];
    });
    return { v: 1, type: 'answers', id, answers, ...(fake.resumeFileName ? { resumeFileName: fake.resumeFileName } : {}) };
  }
  if (type === 'draftAnswer' && fake.draft !== undefined) return { v: 1, type: 'draft', id, text: fake.draft };
  return undefined;
}
