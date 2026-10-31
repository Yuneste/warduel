// Writes public/index.html from pages/index.html, filling in the real game settings,
// so the page never shows hardcoded numbers.

import { readFile, writeFile } from 'node:fs/promises';
import { loadConfig } from '../lib/config.js';

const config = loadConfig();
const values = {
  questionsPerGame: config.questionsPerGame,
  durationSeconds: config.durationSeconds,
  winScore: config.winScore > 0 ? config.winScore : config.questionsPerGame,
};

const root = new URL('..', import.meta.url);
const template = await readFile(new URL('pages/index.html', root), 'utf8');
const page = template.replace(/\{\{(\w+)\}\}/g, (match, key) => {
  if (!(key in values)) throw new Error(`Unknown placeholder ${match} in pages/index.html`);
  return values[key];
});
await writeFile(new URL('public/index.html', root), page);
console.log('Wrote public/index.html', values);
