/** Must match the native host manifest `name` and the registry keys the MSI writes. */
export const HOST_NAME = 'io.github.raghavsatyadev.jobfinder';

export const PROTOCOL_VERSION = 1;

/** Reconnect backoff; the alarm is a safety net because timers die when the worker sleeps. */
export const RECONNECT = { minMs: 1_000, maxMs: 60_000, alarmName: 'reconnect', alarmMinutes: 1 };

/** readPage limits, in UTF-16 characters. The worst case stays far below Chrome's 64 MB ext→host cap. */
export const READ_LIMITS = {
  textChars: 1_000_000,
  htmlChars: 2_000_000,
  nextDataChars: 4_000_000,
  jsonLdChars: 1_000_000,
  urlChars: 8_192,
  selectorChars: 512,
  idChars: 128,
  defaultTimeoutMs: 30_000,
  minTimeoutMs: 1_000,
  maxTimeoutMs: 120_000,
  settleMs: 1_500,
  selectorPollMs: 250,
  maxQueued: 20,
};

/** Form messages (saveAnswers, answersFor, draftAnswer). */
export const FORM_LIMITS = {
  /** Most fields in one message. */
  maxFields: 300,
  /** Labels, names, ids, placeholders. */
  textChars: 2_000,
  valueChars: 10_000,
  maxOptions: 200,
  optionChars: 300,
  /** Saving and matching are quick; the AI may first wait for the local model to load. */
  saveTimeoutMs: 15_000,
  answersTimeoutMs: 90_000,
  draftTimeoutMs: 300_000,
};
