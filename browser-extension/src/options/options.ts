// Options page: connection switch and test, per-site switches, form answers, fallback reader permission.
import {
  areFieldButtonsOn,
  getSites,
  isBridgeEnabled,
  isRecordingAnswers,
  setBridgeEnabled,
  setFieldButtonsOn,
  setRecordingAnswers,
} from '../shared/settings';
import { ALL_URLS, KNOWN_SITES, isSiteOn, turnSiteOff, turnSiteOn } from '../shared/sites';
import { areSignInsShared, shareSignIns, stopSharingSignIns } from '../shared/signInSharing';
import { getConnection, onConnectionChange } from '../shared/state';
import { describeConnection, renderStatus } from '../shared/statusText';
import { reconnect } from '../shared/ui-messages';

const byId = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;

async function initConnection(): Promise<void> {
  const status = byId('status');
  renderStatus(status, await getConnection());
  onConnectionChange((info) => renderStatus(status, info));

  const bridge = byId<HTMLInputElement>('bridge-toggle');
  bridge.checked = await isBridgeEnabled();
  bridge.addEventListener('change', () => void setBridgeEnabled(bridge.checked));

  const test = byId<HTMLButtonElement>('test');
  const result = byId('test-result');
  test.addEventListener('click', async () => {
    test.disabled = true;
    result.textContent = 'Testing…';
    const info = await reconnect();
    result.textContent = info.state === 'connected' ? 'It works.' : `Not working: ${describeConnection(info).label}.`;
    test.disabled = false;
  });
}

async function initSites(): Promise<void> {
  const list = byId('site-list');
  const sites = [...new Set([...KNOWN_SITES, ...(await getSites())])].sort();
  for (const site of sites) {
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.setAttribute('role', 'switch');
    input.checked = await isSiteOn(site);
    input.addEventListener('change', async () => {
      input.disabled = true;
      if (input.checked) input.checked = await turnSiteOn(site);
      else await turnSiteOff(site);
      input.disabled = false;
    });

    const label = document.createElement('label');
    label.className = 'toggle';
    label.append(input, Object.assign(document.createElement('span'), { textContent: site }));
    const item = document.createElement('li');
    item.append(label);
    list.append(item);
  }
}

async function initReader(): Promise<void> {
  const reader = byId<HTMLInputElement>('reader-toggle');
  reader.checked = await chrome.permissions.contains({ origins: [ALL_URLS] });
  reader.addEventListener('change', async () => {
    reader.disabled = true;
    if (reader.checked) reader.checked = await chrome.permissions.request({ origins: [ALL_URLS] });
    else await chrome.permissions.remove({ origins: [ALL_URLS] });
    reader.disabled = false;
  });
}

async function initSignIns(): Promise<void> {
  const toggle = byId<HTMLInputElement>('signins-toggle');
  toggle.checked = await areSignInsShared();
  toggle.addEventListener('change', async () => {
    toggle.disabled = true;
    if (toggle.checked) toggle.checked = await shareSignIns();
    else await stopSharingSignIns();
    toggle.disabled = false;
  });
}

async function initForms(): Promise<void> {
  const record = byId<HTMLInputElement>('record-toggle');
  record.checked = await isRecordingAnswers();
  record.addEventListener('change', () => void setRecordingAnswers(record.checked));
  const buttons = byId<HTMLInputElement>('buttons-toggle');
  buttons.checked = await areFieldButtonsOn();
  buttons.addEventListener('change', () => void setFieldButtonsOn(buttons.checked));
}

void initConnection();
void initForms();
void initSites();
void initReader();
void initSignIns();
