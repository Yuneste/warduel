// Local server for development: serves public/ and runs the game like Vercel would.
// Usage: npm run dev, then open http://localhost:8080 in two tabs. PORT changes the port.
// Without REDIS_URL and DATABASE_URL, game state and the leaderboard stay in this process's memory.

import { createReadStream, existsSync, statSync } from 'node:fs';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import gameServer from '../api/game.js';
import leaderboard from '../api/leaderboard.js';

const PUBLIC = fileURLToPath(new URL('../public', import.meta.url));
const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
};

function serveStatic(request, response) {
  const { pathname } = new URL(request.url, 'http://localhost');
  const clean = normalize(decodeURIComponent(pathname)).replace(/^([/\\]|\.\.)+/, '');
  const candidates = [clean || 'index.html', `${clean}.html`].map(file => join(PUBLIC, file));
  const file = candidates.find(path => existsSync(path) && statSync(path).isFile());
  if (!file) {
    response.writeHead(404, { 'Content-Type': 'text/plain' });
    response.end('Not found');
    return;
  }
  response.writeHead(200, { 'Content-Type': TYPES[extname(file)] ?? 'application/octet-stream' });
  createReadStream(file).pipe(response);
}

gameServer.removeAllListeners('request');
gameServer.on('request', (request, response) => {
  if (request.url.startsWith('/api/leaderboard')) {
    leaderboard(request, response);
  } else {
    serveStatic(request, response);
  }
});

const port = Number(process.env.PORT ?? 8080);
gameServer.listen(port, () => console.log(`MathWars on http://localhost:${port}`));
