// The "share sign-ins" switch: the cookies permission plus the known job boards' origins.
// Sideload build only; the store build (`__SIGNINS__` false) never calls these.
import { KNOWN_SITES, originsFor } from './sites';

export function areSignInsShared(): Promise<boolean> {
  return chrome.permissions.contains({ permissions: ['cookies'] });
}

/** Call from a click handler: Chrome only shows the permission prompt on a user gesture. */
export function shareSignIns(): Promise<boolean> {
  return chrome.permissions.request({ permissions: ['cookies'], origins: KNOWN_SITES.flatMap(originsFor) });
}

export async function stopSharingSignIns(): Promise<void> {
  await chrome.permissions.remove({ permissions: ['cookies'] }).catch(() => false);
}
