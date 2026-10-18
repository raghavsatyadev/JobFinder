// Per-site switches. A site is on only when it is in settings AND its host permission is granted,
// so a popup that closes mid-prompt can never leave a half-enabled site behind.
import { getSites, setSites } from './settings';

/** Job boards offered in the options page; any other site can be turned on from the popup. */
export const KNOWN_SITES = [
  'linkedin.com',
  'glassdoor.com',
  'glassdoor.co.in',
  'ambitionbox.com',
  'naukri.com',
  'indeed.com',
  'wellfound.com',
];

export const ALL_URLS = '<all_urls>';

export function originsFor(site: string): string[] {
  return [`*://${site}/*`, `*://*.${site}/*`];
}

function hostMatches(host: string, site: string): boolean {
  return host === site || host.endsWith(`.${site}`);
}

function hostOf(url: string): string | undefined {
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.hostname : undefined;
  } catch {
    return undefined;
  }
}

/** The site a URL belongs to: a known board if it matches, else the host without "www.". */
export function siteForUrl(url: string): string | undefined {
  const host = hostOf(url);
  if (!host) return undefined;
  return KNOWN_SITES.find((site) => hostMatches(host, site)) ?? host.replace(/^www\./, '');
}

export async function isSiteOn(site: string): Promise<boolean> {
  if (!(await getSites()).includes(site)) return false;
  return chrome.permissions.contains({ origins: originsFor(site) });
}

/** True when the URL belongs to a site that is on. */
export async function isUrlWatched(url: string): Promise<boolean> {
  const host = hostOf(url);
  if (!host) return false;
  const site = (await getSites()).find((s) => hostMatches(host, s));
  return site !== undefined && chrome.permissions.contains({ origins: originsFor(site) });
}

/** Call from a click handler: Chrome only shows the permission prompt on a user gesture. */
export async function turnSiteOn(site: string): Promise<boolean> {
  await setSites([...(await getSites()), site]);
  const granted = await chrome.permissions.request({ origins: originsFor(site) });
  if (!granted) await turnSiteOff(site);
  return granted;
}

export async function turnSiteOff(site: string): Promise<void> {
  await setSites((await getSites()).filter((s) => s !== site));
  // Fails harmlessly when the origin is only covered by the fallback reader's <all_urls> grant.
  await chrome.permissions.remove({ origins: originsFor(site) }).catch(() => false);
}
