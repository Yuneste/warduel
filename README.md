# MathWars

[![CI](https://github.com/Yuneste/warduel/actions/workflows/ci.yml/badge.svg)](https://github.com/Yuneste/warduel/actions/workflows/ci.yml)

Real-time multiplayer math duel: two players race to solve 20 problems in 60 seconds, on a chalkboard.

**[▶ Play live](https://mathwars-psi.vercel.app)**

![Three challenge types on a phone: Classic with a "5 in a row" streak and chalk dust, True or false, and Which is bigger with powers](docs/screenshots/gameplay.png)

| Desktop | Every ending |
|---|---|
| ![Desktop: framed chalkboard with the question on the left and the number pad on the right](docs/screenshots/desktop.png) | ![Win, lose and draw screens with nicknames, tally marks and answer stats](docs/screenshots/results.png) |

## Features

- Real-time 1v1 matches via WebSocket: random opponent, or challenge a friend with an invite link or code (they get a popup with 10 seconds to accept)
- Nicknames, and post-game stats with every answer and how long it took
- Leaderboard of public games (no accounts: a random device ID, stored only as a SHA-256 hash)
- Four challenge types: Classic, True or false, Missing number and Which is bigger (fractions, powers, roots); problems get harder through the game and never repeat
- 60-second rounds with live score updates
- Rematch system for consecutive games
- Chalkboard design built phone-first: scores drawn as tally marks, on-screen number pad with haptic feedback
- Full keyboard play on desktop (digits, Backspace, minus, Enter; arrows or T/F in button modes)
- Sound synthesized live with the Web Audio API (no audio files), chalk-dust effects, streak call-outs; mute and reduced-motion respected

## Tech Stack

**Backend:**
- Node.js on Vercel Functions, with WebSockets through `ws`
- Redis (Upstash) for shared game state and pub/sub between function instances
- PostgreSQL (Neon) for the leaderboard
- Rate limiting and connection monitoring

**Frontend:**
- Vanilla JavaScript ES6 modules
- WebSocket API for real-time communication
- Hand-written CSS with design tokens, no framework
- Self-hosted fonts (no third-party requests)

## Quick Start

Needs Node.js 22 or newer.

```bash
git clone https://github.com/Yuneste/warduel.git
cd warduel
npm install
npm run dev
```

Open `http://localhost:8080` in two browser windows to play. Locally the game state and leaderboard stay in memory unless you set `REDIS_URL` and `DATABASE_URL`.

### Tests

```bash
npm test
TEST_REDIS_URL=redis://localhost:6379 node --test --test-concurrency=1 test/game.test.mjs
```

Besides unit tests for the rules and the leaderboard, `test/game.test.mjs` plays real matches over WebSocket against two running server instances, with the two players of each game connected to different instances: countdown, timer, win score, rematch, forfeit, disconnects, challenges and rate limiting.

## Deploy

The repo deploys to Vercel as static files plus two functions. On a Pro team:

1. Connect the repository to a Vercel project. `vercel.json` sets the Frankfurt region and the function limits.
2. Add Upstash for Redis from the Marketplace, in Frankfurt (`fra1`). Its connection string is picked up from `REDIS_URL` or `KV_URL`.
3. Set `DATABASE_URL` to a Postgres connection string (`postgresql://…?sslmode=require`). The app creates its table if it's missing.

Notes:
- Static pages come from the CDN and don't wake anything. A function starts when someone actually plays.
- Vercel gives no guarantee that two connections reach the same function instance, so game state lives in Redis and messages travel over pub/sub. Without `REDIS_URL` in production, players on different instances can't find each other.
- A connection closes when the function reaches its maximum duration (30 minutes here, a Vercel beta limit). The client then returns to the lobby.

## Project Structure

```
api/
├── game.js          # the WebSocket endpoint
└── leaderboard.js   # GET /api/leaderboard
lib/
├── engine.js        # rules and state machine, every change under the game's lock
├── gameServer.js    # one Connection per player: rate limit, heartbeat, timers
├── store.js         # Redis store and an in-memory one with the same interface
├── questions.js     # question generator for all challenge types
├── leaderboard.js   # results by hashed device token
├── names.js         # nickname cleanup
└── config.js        # game settings
pages/index.html     # the single page; scripts/build.mjs fills in the game settings
public/
├── css/style.css    # chalkboard design tokens and layout
├── fonts/           # self-hosted Cabin Sketch + Lexend (OFL)
└── js/modules/      # websocket, messageHandlers, uiController, sound, effects, ...
```

## How It Works

1. A player picks a name and either joins public matchmaking or creates an invite link for a friend.
2. Opening an invite link challenges its creator, who has 10 seconds to accept.
3. Both players are introduced, a 3-second countdown runs, and the 60-second clock starts only after it.
4. Each player solves their own 20 problems of the same challenge type; scores update live on both screens.
5. The first to 20 correct answers wins on the spot, otherwise the higher score when time runs out.
6. The result screen shows every answer and its timing; players can rematch, find someone new, or go back to the menu.

## Key Features

**Backend:**
- Works when a game's two players are on different server instances: state in Redis, a lock per game, and messages published in order under that lock
- Each player's connection holds a copy of the game's timers and the first to fire does the work, so a game finishes as long as one player is connected, and a stale timer can never end a rematch
- Matchmaking, single-use private rooms and challenge handshakes
- Rate limiting (10 messages per second per player) and heartbeat-based timeout detection
- Leaderboard with hashed device tokens; the raw token never leaves the connection handler

**Frontend:**
- Vanilla ES modules, no framework
- Phone-first layout, keyboard support, `prefers-reduced-motion`, visible focus styles
- Sound synthesized with the Web Audio API; chalk-dust effects and streak callouts

**Testing:**
- Unit tests for the rules and the leaderboard, which runs against real Postgres SQL through PGlite
- Every challenge type is checked against an independent answer key
- Integration tests that play real matches across two server instances; CI repeats them on real Redis

## License

MIT License - See [LICENSE](LICENSE) file for details.
