// Service worker entry: wires the native port to the page reader, job lookup and UI requests.
import { RECONNECT } from '../shared/constants';
import { errorMsg, type HostToExt } from '../shared/protocol';
import { isBridgeEnabled, onBridgeEnabledChange, onSitesChange } from '../shared/settings';
import type { UiRequest } from '../shared/ui-messages';
import { FormAssistant } from './forms';
import { HostRequests } from './hostRequests';
import { JobLookup } from './jobLookup';
import { NativeLink } from './nativeLink';
import { PageReader } from './pageReader';
import { readSignIns } from './signIns';

const link = new NativeLink(onHostMessage);
const reader = new PageReader((msg) => link.send(msg));
const jobs = new JobLookup(link);
const requests = new HostRequests(link);
const forms = new FormAssistant(requests);
link.onClosed = () => requests.failAll();

function onHostMessage(msg: HostToExt): void {
  if (requests.onReply(msg)) return;
  switch (msg.type) {
    case 'readPage':
      reader.enqueue(msg);
      break;
    case 'getCookies':
      void readSignIns(msg).then((reply) => link.send(reply));
      break;
    case 'answersSaved':
    case 'answers':
    case 'draft':
      break; // A reply that came after its request gave up.
    case 'job':
    case 'error':
      void jobs.onReply(msg);
      break;
    default: {
      const unknown = msg as { type: string; id?: unknown };
      const id = typeof unknown.id === 'string' ? unknown.id : undefined;
      link.send(errorMsg('unknown_type', `Unknown message type "${unknown.type}".`, id));
    }
  }
}

async function startIfEnabled(): Promise<void> {
  if (await isBridgeEnabled()) link.start();
  else link.stop();
}

// Runs on every worker wake-up; an open native port then keeps the worker alive.
void startIfEnabled();
void chrome.alarms.create(RECONNECT.alarmName, { periodInMinutes: RECONNECT.alarmMinutes });

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === RECONNECT.alarmName && !link.isOpen) void startIfEnabled();
});

onBridgeEnabledChange(() => void startIfEnabled());

// The form content script follows the sites that are on.
const syncForms = () => void forms.syncContentScript().catch(() => undefined);
syncForms();
onSitesChange(syncForms);
chrome.permissions.onAdded.addListener(syncForms);
chrome.permissions.onRemoved.addListener(syncForms);

chrome.tabs.onUpdated.addListener((tabId, info) => {
  // `url` is only reported for sites we hold a permission for, which is exactly the watched set.
  if (info.url && !reader.owns(tabId)) void jobs.lookup(tabId, info.url);
});

chrome.tabs.onRemoved.addListener((tabId) => void jobs.forget(tabId));

chrome.runtime.onMessage.addListener((request: UiRequest, sender, sendResponse) => {
  if (sender.id !== chrome.runtime.id) return false;
  if (request.kind === 'reconnect') {
    void isBridgeEnabled().then(async (enabled) => {
      if (!enabled) {
        link.stop();
        sendResponse({ state: 'off' });
        return;
      }
      sendResponse(await link.restart());
    });
    return true;
  }
  if (request.kind === 'autofillTab' || request.kind === 'autofill') {
    const tabId = request.kind === 'autofillTab' ? request.tabId : sender.tab?.id;
    if (tabId === undefined) return false;
    void forms
      .autofill(tabId)
      .catch(() => ({ problem: 'failed' as const, filled: 0, kept: 0, failed: 0, unmatched: 0, files: [] }))
      .then(sendResponse);
    return true;
  }
  if (request.kind === 'formSubmitted') {
    void forms.saved(sender.url, sender.tab?.url, request.fields).catch(() => undefined);
    return false;
  }
  if (request.kind === 'draftAnswer') {
    void forms
      .draft(sender.tab?.url, sender.url, request.field, request.current)
      .catch(() => ({ problem: 'failed' as const }))
      .then(sendResponse);
    return true;
  }
  if (request.kind === 'lookupTab') {
    void jobs.lookup(request.tabId, request.url).then(() => sendResponse());
    return true;
  }
  return false;
});
