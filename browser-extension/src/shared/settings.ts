// User settings in chrome.storage.local.

const BRIDGE_KEY = 'bridgeEnabled';
const SITES_KEY = 'sites';

export async function isBridgeEnabled(): Promise<boolean> {
  const got = await chrome.storage.local.get(BRIDGE_KEY);
  return got[BRIDGE_KEY] !== false;
}

export function setBridgeEnabled(enabled: boolean): Promise<void> {
  return chrome.storage.local.set({ [BRIDGE_KEY]: enabled });
}

export function onBridgeEnabledChange(listener: (enabled: boolean) => void): void {
  chrome.storage.onChanged.addListener((changes, area) => {
    const change = changes[BRIDGE_KEY];
    if (area === 'local' && change) listener(change.newValue !== false);
  });
}

/** Sites the user turned on, as bare hostnames like "linkedin.com". */
export async function getSites(): Promise<string[]> {
  const got = await chrome.storage.local.get(SITES_KEY);
  return (got[SITES_KEY] as string[] | undefined) ?? [];
}

export function setSites(sites: string[]): Promise<void> {
  return chrome.storage.local.set({ [SITES_KEY]: [...new Set(sites)].sort() });
}

const RECORD_KEY = 'recordAnswers';
const BUTTONS_KEY = 'fieldButtons';

/** Save what the user typed when a form is sent on a site that is on. On unless turned off. */
export async function isRecordingAnswers(): Promise<boolean> {
  const got = await chrome.storage.local.get(RECORD_KEY);
  return got[RECORD_KEY] !== false;
}

export function setRecordingAnswers(on: boolean): Promise<void> {
  return chrome.storage.local.set({ [RECORD_KEY]: on });
}

/** Show the Autofill and AI help buttons next to a form field in focus. On unless turned off. */
export async function areFieldButtonsOn(): Promise<boolean> {
  const got = await chrome.storage.local.get(BUTTONS_KEY);
  return got[BUTTONS_KEY] !== false;
}

export function setFieldButtonsOn(on: boolean): Promise<void> {
  return chrome.storage.local.set({ [BUTTONS_KEY]: on });
}

/** Calls [listener] when the list of sites changes. */
export function onSitesChange(listener: () => void): void {
  chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && changes[SITES_KEY]) listener();
  });
}
