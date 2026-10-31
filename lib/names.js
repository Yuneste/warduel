// Cleans up player-chosen nicknames before they are shown to anyone.

export const MAX_NAME_LENGTH = 16;

// Letters and digits of any script, plus a few separators people use in names
const NOT_ALLOWED = /[^\p{L}\p{N} ._'-]/gu;

/** @returns the cleaned name, or null if nothing usable is left (the caller picks a default) */
export function sanitizeName(raw) {
  if (typeof raw !== 'string') return null;
  let cleaned = raw.replace(/\s+/g, ' ').replace(NOT_ALLOWED, '').trim();
  const characters = Array.from(cleaned);
  if (characters.length > MAX_NAME_LENGTH) {
    cleaned = characters.slice(0, MAX_NAME_LENGTH).join('').trim();
  }
  return cleaned === '' ? null : cleaned;
}
