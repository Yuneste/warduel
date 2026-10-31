// The game's WebSocket endpoint on Vercel: GET /api/game with an Upgrade header.

import { waitUntil } from '@vercel/functions';
import { loadConfig } from '../lib/config.js';
import { createEngine } from '../lib/engine.js';
import { createGameServer } from '../lib/gameServer.js';
import { sharedLeaderboard } from '../lib/leaderboard.js';
import { sharedStore } from '../lib/store.js';

// waitUntil takes the promise and returns nothing, so work handed to it can still finish
// after the player's socket is gone
const background = promise => {
  waitUntil(promise);
  return promise;
};

const store = sharedStore();
const engine = createEngine({ store, config: loadConfig(), leaderboard: sharedLeaderboard(), background });

export default createGameServer({ store, engine, background });
