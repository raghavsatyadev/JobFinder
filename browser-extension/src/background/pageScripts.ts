// Functions run inside the page via chrome.scripting.executeScript. They are serialized, so they
// must not use imports or anything outside their own body.
import type { PageData } from '../shared/protocol';

export interface ExtractLimits {
  textChars: number;
  htmlChars: number;
  nextDataChars: number;
  jsonLdChars: number;
}

/** Text and HTML are cut at the limit; JSON blocks are dropped whole so whatever is sent still parses. */
export function extractPage(limits: ExtractLimits, includeHtml: boolean): PageData {
  const truncated: string[] = [];
  const cut = (field: string, value: string, max: number): string => {
    if (value.length <= max) return value;
    truncated.push(field);
    return value.slice(0, max);
  };

  const jsonLd: string[] = [];
  let ldBudget = limits.jsonLdChars;
  for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
    const content = (script.textContent ?? '').trim();
    if (!content) continue;
    if (content.length > ldBudget) {
      if (!truncated.includes('jsonLd')) truncated.push('jsonLd');
      continue;
    }
    jsonLd.push(content);
    ldBudget -= content.length;
  }

  let nextData = document.getElementById('__NEXT_DATA__')?.textContent?.trim() || null;
  if (nextData && nextData.length > limits.nextDataChars) {
    truncated.push('nextData');
    nextData = null;
  }

  const data: PageData = {
    finalUrl: location.href,
    title: document.title,
    text: cut('text', document.body?.innerText ?? '', limits.textChars),
    jsonLd,
    nextData,
    truncated,
  };
  if (includeHtml) data.html = cut('html', document.documentElement.outerHTML, limits.htmlChars);
  return data;
}

/** "found", "missing", or "invalid" for a selector that does not parse. */
export function probeSelector(selector: string): 'found' | 'missing' | 'invalid' {
  try {
    return document.querySelector(selector) ? 'found' : 'missing';
  } catch {
    return 'invalid';
  }
}
