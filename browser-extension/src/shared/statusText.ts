// User-facing wording for each connection state, shared by the popup and the options page.
import { APP_DOWNLOAD_URL } from './constants';
import type { ConnectionInfo, ConnectionState } from './state';

const TEXT: Record<ConnectionState, { label: string; hint: string }> = {
  off: {
    label: 'Turned off',
    hint: 'The connection to the JobFinder app is turned off in the extension options.',
  },
  connecting: { label: 'Connecting…', hint: '' },
  connected: { label: 'Connected to JobFinder', hint: '' },
  app_not_running: {
    label: 'JobFinder app is not running',
    hint: 'Open the JobFinder app on this PC. The extension reconnects by itself.',
  },
  host_not_installed: {
    label: 'The JobFinder desktop app is not installed',
    hint:
      'This extension works only together with the free JobFinder app for Windows. Install the app, ' +
      'open it once, then click Try again. Already installed? Repair it, or restart the browser.',
  },
  forbidden: {
    label: 'JobFinder blocked this extension',
    hint: 'This copy of the extension has an id the JobFinder app does not allow. Reinstall the app or the extension.',
  },
};

export function describeConnection(info: ConnectionInfo): { label: string; hint: string } {
  const text = TEXT[info.state];
  if (info.state === 'connected' && info.appVersion) {
    return { ...text, label: `${text.label} ${info.appVersion}` };
  }
  return text;
}

/**
 * Fills a status block: `[data-part=label]`, `[data-part=hint]`, the optional `[data-part=download]`
 * link (shown only when the app is not installed), and `data-state` for styling.
 */
export function renderStatus(root: HTMLElement, info: ConnectionInfo): void {
  const { label, hint } = describeConnection(info);
  root.dataset.state = info.state;
  root.querySelector<HTMLElement>('[data-part=label]')!.textContent = label;
  const hintEl = root.querySelector<HTMLElement>('[data-part=hint]')!;
  hintEl.textContent = hint;
  hintEl.hidden = hint === '';
  const download = root.querySelector<HTMLAnchorElement>('[data-part=download]');
  if (download) {
    download.href = APP_DOWNLOAD_URL;
    download.hidden = info.state !== 'host_not_installed';
  }
}
