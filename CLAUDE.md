# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

MathWars (repo name `warduel`): a real-time 1v1 math quiz game. A Node.js backend on Vercel Functions talks to a vanilla ES6-module frontend over a raw WebSocket. The frontend is served as static files.

## Game requirements (source of truth)

- Two players are matched automatically: the first to connect waits, and the second fills the game.
- Each player gets their **own** 20 random questions. Both players get the same **challenge type** (`ChallengeType`):
  - Classic: `7 × 8`
  - True or false: `7 × 8 = 54`
  - Missing number: `? × 8 = 56`
  - Which is bigger: `3/4 or 0.7`

  Public games get a random type (or `game.public-challenge-type`), a private host picks one, and a rematch keeps it. Problems get harder over the game (tiers within the configured `game.*` ranges) and never repeat within a list. Subtraction never goes negative, and division always comes out even.
- 60-second round with a 3-second countdown before it. The first player to reach 20 correct answers wins immediately. Otherwise the higher score wins when time runs out, and equal scores are a draw.
- Live score updates go to both players after every answer. Each question can be answered only once, and answers after the time is up are rejected.
- After the game, both players must agree to a rematch, which starts without a countdown. Forfeiting or disconnecting during a game gives the opponent the win. Leaving during the queue or countdown cancels the match.
- Mobile stability: the client sends a `HEARTBEAT` every 5s, and the server closes connections that have been silent for more than 10s. Limit: 10 messages/second per player.
- The UI is English. The Impressum and Datenschutz pages stay German (legally required).

## Commands

Works on Node 22 and newer. CI runs on 22 and 24, and repeats the game scenarios on real Redis.

```bash
npm run dev                                  # http://localhost:8080 (open two tabs to play); PORT changes the port
npm run build                                # writes public/index.html from pages/index.html
npm test                                     # all tests (~80s, mostly the WebSocket scenarios)
node --test --test-name-pattern="forfeit" test/game.test.mjs   # a single scenario
TEST_REDIS_URL=redis://localhost:6379 node --test --test-concurrency=1 test/game.test.mjs   # on real Redis
```

Tests:
- `test/rules.test.mjs`: the question generator, checked for every challenge type against the solver in `test/support.mjs`, an independent answer key sharing no code with the generator; nicknames
- `test/leaderboard.test.mjs`: the leaderboard on real Postgres SQL (PGlite) and in memory, plus the table the old Java version created
- `test/game.test.mjs`: starts two server instances with 5-second rounds and win score 3, and plays real matches through `TestPlayer` clients, with players connecting to the instances in turn so each game is split across them

When you change game behavior, add or adjust a scenario there. Without `REDIS_URL`/`KV_URL` and `DATABASE_URL`, game state and the leaderboard stay in the process's memory. That's fine locally and wrong in production. `TEST_REDIS_URL` keys are prefixed per run, so tests never touch live games.

## Architecture

### Backend (`api/`, `lib/`)

- **Why it's built this way:** Vercel doesn't guarantee that two WebSocket connections reach the same function instance, and instances disappear when idle. So no game state may live only in one instance's memory. Everything shared goes through `lib/store.js` (Redis, or an in-memory store with the same interface).
- **Protocol for joining:** after connecting to `/api/game`, the client must send `JOIN_GAME` with an optional `playerName`. To create a private game it adds `createRoom: true` (the server answers `ROOM_CREATED {roomCode}`). Opening someone's link (or entering its code in the lobby) sends `roomCode`, which **challenges** the host:
  - the host gets `CHALLENGE {fromName, timeoutSeconds}` (a popup) and answers with `CHALLENGE_RESPONSE {accept}`
  - the challenger gets `CHALLENGE_STATUS` with one of SENT, DECLINED, TIMED_OUT, BUSY or HOST_LEFT; the host gets CANCELLED or TIMED_OUT
  - only an accept starts the game; otherwise the link stays valid. The pending challenger waits in `game.pendingChallenger`, which is not a player, and the timeout is `challengeTimeoutSeconds` (default 10). Connections that don't join within 15s are closed. The client sends `LEAVE` before closing its socket, because on Vercel a closed socket reaches the function 2.5 to 5 seconds later, longer than the countdown. Before the countdown, both players get `GAME_STATE READY` with `yourName`/`opponentName`. `GAME_OVER` carries `yourStats` (including every answer with its timing) and `opponentStats` (totals only).
- **`lib/engine.js`** holds the rules and the state machine `WAITING → READY → RUNNING → FINISHED` (rematch resets `FINISHED → READY`). A game is one JSON document. Every change goes through `update()`: take the game's lock, load, change, save, publish the queued messages in order. Publishing under the lock is what keeps both players' event order consistent. Public matchmaking uses one `queue` key; private games use `room:<code>` by a 6-character code (no look-alike characters), and a code works once and dies with its host. Nicknames go through `sanitizeName`, duplicate names in one game get a " (2)" suffix, and the winner is always decided by score, never by name. Player identity is a random ID per connection.
- **`lib/gameServer.js`** has one `Connection` per player: rate limit, join timeout, heartbeat check (silent for more than 10s while a round runs closes the socket), and handling messages one at a time. Work that must outlive the socket goes through `background` (`waitUntil`), or the platform can freeze the instance when the last socket closes.
- **Timers:** there is no shared scheduler. The engine publishes `_TIMER` messages and each player's connection keeps its own copy. When one fires it calls the engine, and the state check makes the second call a no-op, so a game ends as long as one player is connected. The clock starts only on `READY → RUNNING`, in `startRound`. `_CANCEL_TIMERS` clears them when a game ends, and `roundTimeUp` checks the round number so a stale timer can't end a rematch. Message types starting with `_` never reach the browser.
- **Adding a message type:** add a `case` in `Connection.handle` (client→server) and/or a handler in `messageHandlers.js` (server→client). The server sends `GAME_STATE` (RUNNING) when a round starts, which the client needs for Forfeit.
- **`lib/config.js`** holds the game settings, each overridable with `GAME_*` environment variables.
- **Answers are always ints**: the number for NUMBER, 1/0 for TRUE_FALSE, and the option index for CHOICE. `QUESTION` carries `format` and `options`, and `formatAnswer` turns an answer into words for the stats. The chalk font only has ² and ³, so powers stay squares and cubes.
- **`lib/leaderboard.js`** holds wins, losses and draws by the SHA-256 hash of a random device token that the browser generates (`mathwars.token` in localStorage) and sends in `JOIN_GAME`. `gameServer.js` hashes it immediately, so the raw token is never stored or logged, not even in Redis. Only public games count, and a forfeit or disconnect counts only after 10s of play. `GET /api/leaderboard` (token in the `X-Player-Token` header) returns the top 10 plus the caller's own line. The table is the one the Java version created, so don't rename its columns.
- **Origin check:** only pages from the same host may open `/api/game` (the request's `x-forwarded-host` or `host` against `Origin`).
- **`scripts/build.mjs`** fills `questionsPerGame`, `durationSeconds` and `winScore` into `pages/index.html`, which shows them and exposes them as `<body data-*>` for the JS. Never hardcode those numbers in the frontend.

### Frontend (`pages/`, `public/`)

- README screenshots live in `docs/screenshots/`. Retake them when the UI changes visibly.
- `pages/index.html` is the single page. Its four `.screen` sections (lobby, waiting/countdown, game, result) are switched with the `hidden` attribute through `ui.showScreen()`, so only one is visible at a time. `#toast` shows messages on top of any screen. It loads `public/js/game-modular.js` as an ES module. `public/index.html` is generated, so edit the one in `pages/`.
- Data flow in `public/js/modules/`:
  - `websocket.js`: connection, heartbeat, reload on loss. Events from a replaced socket are ignored.
  - `messageHandlers.js`: routes by `type`. It holds the ✓/✗ answer mark for `FEEDBACK_HOLD_MS` before the next question replaces it. In a `SCORE_UPDATE`, `wasCorrect` is `null` when the update is about the opponent's answer.
  - `gameState.js` (state, settings, client timer) and `uiController.js` (DOM only, no logic; also draws the tally-mark scores as SVG).
  - `sound.js`: every sound is synthesized with the Web Audio API (no audio files). The context is created on the first user gesture, and mute is stored as `mathwars.muted`. `effects.js`: chalk dust and streak callouts in `#effects-layer`, skipped under `prefers-reduced-motion`. `messageHandlers.js` triggers both: correct/wrong, streaks at 3/5/10/15, the opponent scoring, the countdown intro, ticks in the last 5 seconds, and the result.
  - `gameActions.js`: play, cancel, give up (needs a second tap), rematch, find a new opponent, back to the main menu. `inputHandler.js`: number pad plus keyboard (digits, Backspace, `-`, Enter, Escape). `domElements.js`: cached element lookups.
- **Design: "Chalkboard duel".** All tokens are CSS variables at the top of `public/css/style.css`, the only stylesheet:
  - colors: board `#263d33`, chalk `#eef0e8`, you = yellow `#f2d45c`, opponent = pink `#f3a1b5`, correct = green `#a8e6a1`, wood tray
  - fonts: Cabin Sketch for big chalk text (titles, question, numbers on keys) and Lexend for everything else, including filled buttons, where the hatched chalk face loses contrast
  - fonts are **self-hosted** in `public/fonts/` with their OFL licenses. Don't load anything from Google or other CDNs: that's a GDPR problem in Germany, and the privacy policy doesn't cover it.
  - phone-first: the board fills the screen and the wooden `.tray` holds the legal links. From 820px up, the board is framed on a wall and the game switches to two columns.

## Roadmap

Work happens on branch `refresh`, one commit per phase. Phases 1 (cleanup), 2 (backend bugs), 3 (tests, CI, JDK 25 support) and 4 (chalkboard redesign) are done. Phase 5 is done: 5a (nicknames, invite links, stats), 5b (challenge types), 5c (leaderboard) and 5d (sound and game feel). If you change what is stored or sent, update `public/datenschutz.html` (German privacy policy) to match.

6. **Hosting:** on Vercel (team project `mathwars`, functions in Frankfurt), with Redis on Upstash through `REDIS_URL`/`KV_URL` and Postgres on Neon through `DATABASE_URL`. It replaced a Spring Boot server on Render, whose free plan took over a minute to wake up.

When you test against the live site, play only **private** games to the end, or cancel public matches during the countdown. Finished public games put test names on the real leaderboard.

## Conventions

- Write code comments and log messages in English.
- Verify UI changes in a real browser, including states only reachable by interacting (countdown, wrong and correct answers, result variants, rematch, opponent leaving), at desktop and phone widths.
