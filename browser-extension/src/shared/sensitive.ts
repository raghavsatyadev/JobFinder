// Fields the extension never reads, sends or fills: passwords, card and bank details, ID numbers
// and one-time codes. The desktop app runs the same check again (SensitiveFields.kt).
import type { FieldInfo } from './protocol';

const SENSITIVE_AUTOCOMPLETE = new Set(['current-password', 'new-password', 'one-time-code', 'transaction-amount']);

/** Matched against normalised words (lower case, single spaces); "pin code" is a postal code. */
const SENSITIVE_WORDS = new RegExp(
  '\\b(password|passcode|passwd|pwd|cvv|cvc|csc|cvv2|card number|card no|credit card|debit card|cardholder|' +
    'card holder|expiry date|expiration date|card expiry|iban|swift|bic|routing number|sort code|ifsc|bank|' +
    'account number|account no|acct|ssn|social security|aadhaar|aadhar|uidai|pan|pan number|pan card|passport|' +
    'national id|national identification|nin|tax id|tin|itin|ein|driver s licen[cs]e|driving licen[cs]e|' +
    'licen[cs]e number|otp|one time code|one time password|verification code|security code|security answer|' +
    'secret|pin)\\b(?! code)',
);

const FILLER = new Set(['please', 'kindly', 'enter', 'provide', 'your', 'the', 'a', 'an', 'required', 'optional', 'mandatory', 'field']);

/** Lower case words of letters and digits, camelCase and snake_case split, filler words out. */
export function normalise(text: string): string {
  return text
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .trim()
    .split(' ')
    .filter((w) => w && !FILLER.has(w))
    .join(' ');
}

export function isSensitive(field: FieldInfo): boolean {
  const type = field.inputType.toLowerCase();
  if (type === 'password' || type === 'hidden') return true;
  const auto = (field.autocomplete ?? '').toLowerCase().split(/\s+/);
  if (auto.some((token) => token.startsWith('cc-') || SENSITIVE_AUTOCOMPLETE.has(token))) return true;
  const words = [field.label, field.ariaLabel, field.placeholder, field.name, field.elementId]
    .filter((s): s is string => !!s)
    .map(normalise)
    .join(' | ');
  if (SENSITIVE_WORDS.test(words)) return true;
  return type !== 'tel' && looksLikeCard(field.value ?? '');
}

/** 13–19 digits (spaces and dashes allowed) that pass the Luhn check. */
export function looksLikeCard(value: string): boolean {
  const trimmed = value.trim();
  if (!/^[0-9 -]{13,23}$/.test(trimmed)) return false;
  const digits = trimmed.replace(/\D/g, '');
  if (digits.length < 13 || digits.length > 19) return false;
  let sum = 0;
  [...digits].reverse().forEach((c, i) => {
    let d = Number(c);
    if (i % 2 === 1) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
  });
  return sum % 10 === 0;
}
