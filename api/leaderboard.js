// GET /api/leaderboard: the top players; with an X-Player-Token header also the caller's own line.
// The token travels in a header rather than the URL so it doesn't end up in access logs.

import { sharedLeaderboard } from '../lib/leaderboard.js';

export default async function handler(request, response) {
  response.setHeader('Cache-Control', 'no-store');
  try {
    const board = await sharedLeaderboard().board(request.headers['x-player-token']);
    response.statusCode = 200;
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify(board));
  } catch (error) {
    console.error('Could not load the leaderboard:', error.message);
    response.statusCode = 503;
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ error: 'Leaderboard unavailable' }));
  }
}
