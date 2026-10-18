// User-facing wording for autofill and AI help, shared by the popup and the in-page buttons.
import type { AutofillResult, FormProblem } from './ui-messages';

const PROBLEMS: Record<FormProblem, string> = {
  not_connected: 'JobFinder is not connected. Open the JobFinder app on this PC and try again.',
  no_fields: 'No form fields found on this page.',
  no_permission: 'JobFinder is not turned on for this site. Turn it on in the JobFinder popup.',
  timeout: 'The JobFinder app did not answer in time. Try again.',
  ai_failed: 'The AI could not write an answer. Check the AI model in JobFinder settings.',
  failed: 'Something went wrong. Try again.',
};

export function describeProblem(problem: FormProblem): string {
  return PROBLEMS[problem];
}

/** One or two sentences: what was filled, and what the user still has to do. */
export function describeAutofill(r: AutofillResult): string {
  if (r.problem) return describeProblem(r.problem);
  const parts: string[] = [];
  parts.push(r.filled === 0 ? 'No saved answers fit this form.' : `Filled ${r.filled} ${r.filled === 1 ? 'field' : 'fields'}. Check them before you send the form.`);
  if (r.kept > 0) parts.push(`Left ${r.kept} you already filled.`);
  if (r.files.length > 0) {
    const resume = r.resumeFileName ? ` (${r.resumeFileName})` : '';
    parts.push(`Attach your resume${resume} to “${r.files[0]}” yourself: browsers do not let extensions choose files.`);
  }
  return parts.join(' ');
}
