/**
 * Client-side mirror of `PersonalDataRedactor.java` for contexts sent directly from the browser to a third-party
 * live-call provider (Tavus `conversational-context`). Keeps direct identifiers out of earlier conversation turns.
 */
const EMAIL_PLACEHOLDER = '[EMAIL]'
const PHONE_PLACEHOLDER = '[PHONE]'
const ID_NUMBER_PLACEHOLDER = '[ID_NUMBER]'

const EMAIL_PATTERN = /(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+){0,63}\.[A-Za-z]{2,}/g
// Match Unicode decimal digits (\p{Nd}) to mirror Java's Pattern.UNICODE_CHARACTER_CLASS \d (including full-width IME digits).
const DIGIT = '\\p{Nd}'
const SEP = '[ \\u00A0\\u202F\\u3000\\u2011\\u2013－-]'
const NRIC_PATTERN = new RegExp(
  `(?<![${DIGIT}+＋\\u2011\\u2013－-])(?:${DIGIT}{6}${SEP}${DIGIT}{2}${SEP}${DIGIT}{4}|${DIGIT}{12})(?![${DIGIT}\\u2011\\u2013－-])`,
  'gu',
)
const PHONE_PATTERN = new RegExp(
  `(?<![A-Za-z${DIGIT}+＋])(?:` +
    `[+＋]${DIGIT}{1,3}${SEP}?(?:(?:[(（]${DIGIT}{1,4}[)）]|${DIGIT}{1,4})(?:${SEP}?${DIGIT}{3,4}){2}|${DIGIT}{4}${SEP}?${DIGIT}{4})` +
    `|(?:[(（]${DIGIT}{2,4}[)）]|${DIGIT}{2,4})(?:${SEP}?${DIGIT}{3,4}){2}` +
    `)(?![A-Za-z${DIGIT}])`,
  'gu',
)

/** Replaces recognizable email addresses, NRIC-like ID numbers, and phone numbers with placeholders. */
export function redactPersonalData(text: string): string {
  if (!text) return text
  return text
    .replace(EMAIL_PATTERN, EMAIL_PLACEHOLDER)
    .replace(NRIC_PATTERN, ID_NUMBER_PLACEHOLDER)
    .replace(PHONE_PATTERN, PHONE_PLACEHOLDER)
}
