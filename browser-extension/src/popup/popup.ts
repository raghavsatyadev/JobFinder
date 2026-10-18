// Toolbar popup: connection state, the current tab's job, and the switch for the current site.
import { isSiteOn, siteForUrl, turnSiteOff, turnSiteOn } from '../shared/sites';
import { getConnection, getTabJob, onConnectionChange, onTabJobChange, type TabJob } from '../shared/state';
import { renderStatus } from '../shared/statusText';
import { describeAutofill } from '../shared/formText';
import { autofillTab, lookupTab, reconnect } from '../shared/ui-messages';

const byId = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;

const status = byId('status');
const retry = byId<HTMLButtonElement>('retry');
const jobSection = byId('job-section');
const siteSection = byId('site-section');
const siteToggle = byId<HTMLInputElement>('site-toggle');
const formSection = byId('form-section');
const autofill = byId<HTMLButtonElement>('autofill');
const autofillResult = byId('autofill-result');

async function init(): Promise<void> {
  renderStatus(status, await getConnection());
  onConnectionChange((info) => renderStatus(status, info));

  retry.addEventListener('click', async () => {
    retry.disabled = true;
    renderStatus(status, await reconnect());
    retry.disabled = false;
  });
  byId('open-options').addEventListener('click', () => void chrome.runtime.openOptionsPage());

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  const site = tab?.url ? siteForUrl(tab.url) : undefined;
  if (tab?.id === undefined || !tab.url || !site) return;
  const { id: tabId, url } = tab;

  byId('site-name').textContent = site;
  siteSection.hidden = false;
  siteToggle.checked = await isSiteOn(site);
  siteToggle.addEventListener('change', async () => {
    siteToggle.disabled = true;
    if (siteToggle.checked) siteToggle.checked = await turnSiteOn(site);
    else await turnSiteOff(site);
    siteToggle.disabled = false;
    await showJob(tabId, url, siteToggle.checked);
    formSection.hidden = !siteToggle.checked;
  });

  formSection.hidden = !siteToggle.checked;
  autofill.addEventListener('click', async () => {
    autofill.disabled = true;
    autofillResult.textContent = 'Filling the form…';
    autofillResult.textContent = describeAutofill(await autofillTab(tabId));
    autofill.disabled = false;
  });

  onTabJobChange(tabId, (job) => renderJob(job?.url === url ? job : undefined));
  await showJob(tabId, url, siteToggle.checked);
}

async function showJob(tabId: number, url: string, siteOn: boolean): Promise<void> {
  jobSection.hidden = !siteOn;
  if (!siteOn) return;
  const job = await getTabJob(tabId);
  if (job?.url === url) renderJob(job);
  else await lookupTab(tabId, url); // The answer arrives through onTabJobChange.
}

function renderJob(job: TabJob | undefined): void {
  const found = job?.found === true;
  byId('job-empty').hidden = found;
  byId('job-details').hidden = !found;
  if (!found) return;
  byId('job-title').textContent = job.title || 'Untitled job';
  byId('job-company').textContent = job.company || 'Unknown';
  byId('job-score').textContent = typeof job.atsScore === 'number' ? `${Math.round(job.atsScore)} / 100` : 'Not scored yet';
}

void init();
