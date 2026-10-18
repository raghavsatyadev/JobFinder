// State the worker publishes in chrome.storage.session so the popup and options page can render it.

export type ConnectionState =
  | 'off'
  | 'connecting'
  | 'connected'
  | 'app_not_running'
  | 'host_not_installed'
  | 'forbidden';

export interface ConnectionInfo {
  state: ConnectionState;
  appVersion?: string;
  detail?: string;
}

export interface TabJob {
  url: string;
  found: boolean;
  jobId?: string;
  title?: string;
  company?: string;
  atsScore?: number;
}

const CONNECTION_KEY = 'connection';
const tabKey = (tabId: number) => `tabJob:${tabId}`;

export async function getConnection(): Promise<ConnectionInfo> {
  const got = await chrome.storage.session.get(CONNECTION_KEY);
  return (got[CONNECTION_KEY] as ConnectionInfo | undefined) ?? { state: 'connecting' };
}

export function setConnection(info: ConnectionInfo): Promise<void> {
  return chrome.storage.session.set({ [CONNECTION_KEY]: info });
}

export function onConnectionChange(listener: (info: ConnectionInfo) => void): void {
  chrome.storage.onChanged.addListener((changes, area) => {
    const change = changes[CONNECTION_KEY];
    if (area === 'session' && change?.newValue) listener(change.newValue as ConnectionInfo);
  });
}

export async function getTabJob(tabId: number): Promise<TabJob | undefined> {
  const got = await chrome.storage.session.get(tabKey(tabId));
  return got[tabKey(tabId)] as TabJob | undefined;
}

export function setTabJob(tabId: number, job: TabJob): Promise<void> {
  return chrome.storage.session.set({ [tabKey(tabId)]: job });
}

export function clearTabJob(tabId: number): Promise<void> {
  return chrome.storage.session.remove(tabKey(tabId));
}

export function onTabJobChange(tabId: number, listener: (job: TabJob | undefined) => void): void {
  chrome.storage.onChanged.addListener((changes, area) => {
    const change = changes[tabKey(tabId)];
    if (area === 'session' && change) listener(change.newValue as TabJob | undefined);
  });
}
