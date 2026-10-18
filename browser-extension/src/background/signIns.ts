// Answers getCookies: the cookies of the asked-for job sites, for the app's own browser.
import { READ_LIMITS } from '../shared/constants';
import { errorMsg, type CookieData, type CookiesOut, type ErrorMsg, type GetCookiesIn } from '../shared/protocol';
import { areSignInsShared } from '../shared/signInSharing';
import { originsFor } from '../shared/sites';

/** More sites than this in one request is a bad request. */
const MAX_SITES = 200;
const SITE = /^(?=.{1,253}$)([a-z0-9-]+\.)+[a-z]{2,}$/;

export async function readSignIns(req: Partial<GetCookiesIn>): Promise<CookiesOut | ErrorMsg> {
  const id = typeof req.id === 'string' && req.id.length <= READ_LIMITS.idChars ? req.id : undefined;
  if (id === undefined) return errorMsg('bad_request', 'getCookies needs a string "id".');
  const sites = Array.isArray(req.sites) ? req.sites.filter((s): s is string => typeof s === 'string') : [];
  if (sites.length === 0 || sites.length > MAX_SITES || sites.some((s) => !SITE.test(s))) {
    return errorMsg('bad_request', `getCookies needs 1 to ${MAX_SITES} site names such as "linkedin.com".`, id);
  }
  if (!(await areSignInsShared())) {
    return errorMsg('no_permission', 'Sign-in sharing is off. Turn it on in the JobFinder Helper options.', id);
  }
  const cookies: CookieData[] = [];
  const missing: string[] = [];
  try {
    for (const site of new Set(sites)) {
      if (!(await chrome.permissions.contains({ origins: originsFor(site) }))) {
        missing.push(site);
        continue;
      }
      for (const c of await chrome.cookies.getAll({ domain: site })) cookies.push(toData(c));
    }
  } catch (e) {
    return errorMsg('internal', `Could not read cookies: ${String(e)}`, id);
  }
  return { v: 1, type: 'cookies', id, cookies, missing };
}

function toData(c: chrome.cookies.Cookie): CookieData {
  const sameSite = { strict: 'Strict', lax: 'Lax', no_restriction: 'None' }[c.sameSite as string] as CookieData['sameSite'];
  return {
    name: c.name,
    value: c.value,
    domain: c.hostOnly ? c.domain.replace(/^\./, '') : c.domain.startsWith('.') ? c.domain : `.${c.domain}`,
    path: c.path,
    secure: c.secure,
    httpOnly: c.httpOnly,
    ...(sameSite ? { sameSite } : {}),
    ...(c.session || c.expirationDate === undefined ? {} : { expires: c.expirationDate }),
    session: c.session,
  };
}
