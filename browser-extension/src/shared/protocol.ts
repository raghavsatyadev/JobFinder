// Message shapes for the native messaging protocol. PROTOCOL.md is the spec; keep both in sync.

export type ErrorCode =
  | 'bad_request'
  | 'unsupported_version'
  | 'unknown_type'
  | 'no_permission'
  | 'timeout'
  | 'tab_failed'
  | 'extract_failed'
  | 'busy'
  | 'ai_failed'
  | 'internal';

interface Base {
  v: 1;
}

// Extension -> host

export interface HelloOut extends Base {
  type: 'hello';
  id: string;
  extensionVersion: string;
  browser: 'chrome' | 'edge';
}

export interface PageData {
  finalUrl: string;
  title: string;
  text: string;
  jsonLd: string[];
  nextData: string | null;
  html?: string;
  truncated: string[];
}

export interface PageReadOut extends Base, PageData {
  type: 'pageRead';
  id: string;
  elapsedMs: number;
}

export interface JobForUrlOut extends Base {
  type: 'jobForUrl';
  id: string;
  url: string;
}

/** One form field as the page shows it. Strings are cut at FORM_LIMITS.textChars. */
export interface FieldInfo {
  /** Names the field in `answersFor`; unique within one request. */
  ref?: string;
  label?: string;
  name?: string;
  elementId?: string;
  placeholder?: string;
  ariaLabel?: string;
  /** `text`, `email`, `tel`, `number`, `url`, `date`, `textarea`, `select`, `radio`, `checkbox`, `file`, … */
  inputType: string;
  autocomplete?: string;
  /** The visible choices of a select or radio group. */
  options?: string[];
  /** Only in `saveAnswers` (and `draftAnswer`'s `current`). */
  value?: string;
}

export interface SaveAnswersOut extends Base {
  type: 'saveAnswers';
  id: string;
  url: string;
  fields: FieldInfo[];
}

export interface AnswersForOut extends Base {
  type: 'answersFor';
  id: string;
  url: string;
  fields: FieldInfo[];
  ai?: boolean;
}

export interface DraftAnswerOut extends Base {
  type: 'draftAnswer';
  id: string;
  url: string;
  field: FieldInfo;
  current?: string;
}

/** A cookie in the shape of the DevTools protocol's `Network.Cookie`. */
export interface CookieData {
  name: string;
  value: string;
  /** With a leading dot for a domain cookie; the bare host for a host-only cookie. */
  domain: string;
  path: string;
  secure: boolean;
  httpOnly: boolean;
  sameSite?: 'Strict' | 'Lax' | 'None';
  /** Seconds since the epoch; absent for a session cookie. */
  expires?: number;
  session: boolean;
}

export interface CookiesOut extends Base {
  type: 'cookies';
  id: string;
  cookies: CookieData[];
  /** Asked-for sites this browser holds no permission for. */
  missing: string[];
}

// Host -> extension

export interface HelloIn extends Base {
  type: 'hello';
  id: string;
  appVersion: string;
  appRunning?: boolean;
}

export interface StatusIn extends Base {
  type: 'status';
  appRunning: boolean;
  appVersion?: string;
}

export interface ReadPageIn extends Base {
  type: 'readPage';
  id: string;
  url: string;
  waitForSelector?: string;
  timeoutMs?: number;
  includeHtml?: boolean;
}

export interface GetCookiesIn extends Base {
  type: 'getCookies';
  id: string;
  /** Sites such as `linkedin.com`; their subdomains are included. */
  sites: string[];
}

export interface JobIn extends Base {
  type: 'job';
  id: string;
  found: boolean;
  jobId?: string;
  title?: string;
  company?: string;
  atsScore?: number;
}

export interface AnswersSavedIn extends Base {
  type: 'answersSaved';
  id: string;
  saved: number;
  jobId?: string;
}

export type MatchKind = 'exact' | 'synonym' | 'fuzzy' | 'ai';

export interface FieldAnswer {
  ref: string;
  value: string;
  questionKey: string;
  match: MatchKind;
  score: number;
}

export interface AnswersIn extends Base {
  type: 'answers';
  id: string;
  answers: FieldAnswer[];
  jobId?: string;
  resumeFileName?: string;
}

export interface DraftIn extends Base {
  type: 'draft';
  id: string;
  text: string;
}

// Both directions

export interface ErrorMsg extends Base {
  type: 'error';
  id?: string;
  code: ErrorCode;
  message: string;
}

export type ExtToHost =
  | HelloOut
  | PageReadOut
  | JobForUrlOut
  | SaveAnswersOut
  | AnswersForOut
  | DraftAnswerOut
  | CookiesOut
  | ErrorMsg;
export type HostToExt =
  | HelloIn
  | StatusIn
  | ReadPageIn
  | GetCookiesIn
  | JobIn
  | AnswersSavedIn
  | AnswersIn
  | DraftIn
  | ErrorMsg;

/** Requests the extension sends that expect one reply, and that reply's type. */
export interface HostReplies {
  saveAnswers: AnswersSavedIn;
  answersFor: AnswersIn;
  draftAnswer: DraftIn;
}

export function errorMsg(code: ErrorCode, message: string, id?: string): ErrorMsg {
  return { v: 1, type: 'error', code, message, ...(id === undefined ? {} : { id }) };
}

export function newId(): string {
  return `e-${crypto.randomUUID()}`;
}
