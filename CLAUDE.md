# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

MathWars (repo/artifact name `warduel`): a real-time 1v1 math quiz game. Spring Boot 3.5 / Java 17+ backend talking to a vanilla ES6-module frontend over a raw WebSocket. The frontend is served as static resources.

## Game requirements (source of truth)

- Two players are matched automatically: the first to connect waits, and the second fills the game.
- Each player gets their **own** 20 random questions (add/subtract/multiply/divide, configurable via `game.*`). Subtraction never goes negative, and division always comes out even.
- 60-second round with a 3-second countdown before it. The first player to reach 20 correct answers wins immediately. Otherwise the higher score wins when time runs out, and equal scores are a draw.
- Live score updates go to both players after every answer. Each question can be answered only once, and answers after the time is up are rejected.
- After the game, both players must agree to a rematch, which starts without a countdown. Forfeiting or disconnecting during a game gives the opponent the win. Leaving during the queue or countdown cancels the match.
- Mobile stability: the client sends a `HEARTBEAT` every 5s, and the server closes connections that have been silent for more than 10s. Limit: 10 messages/second per player.
- The UI is English. The Impressum and Datenschutz pages stay German (legally required).

## Commands

Works on JDK 17 through 25. CI runs on 17 and 25. On JDK 23+, Lombok only runs because the pom registers it under `annotationProcessorPaths` (with `lombok.version` overridden). Keep that if you touch the compiler plugin.

```bash
./mvnw spring-boot:run                       # http://localhost:8080 (open two tabs to play)
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.port=8090 --app.allowed-origins=http://localhost:8090"
./mvnw clean package                         # target/warduel-0.0.1-SNAPSHOT.jar
./mvnw test                                  # run tests (~45s, mostly the WebSocket integration test)
./mvnw test -Dtest=ClassName#methodName      # single test
```

Tests:
- unit tests for the rules (`QuestionGeneratorServiceTest`), the state machine (`GameSessionTest`), matchmaking (`GameServiceTest`) and the JSON protocol the frontend relies on (`MessageProtocolTest`)
- `GameWebSocketIntegrationTest`, which starts the server on a random port with 5-second rounds and win score 3, and plays real matches through `TestPlayer` clients

When you change game behavior, add or adjust a scenario there. When changing ports, also set `app.allowed-origins`, or the WebSocket handshake is rejected with 403. There's no JS build step or linter: edit files under `src/main/resources/static/` and reload.

## Architecture

### Backend (`src/main/java/com/warduel/warduel/`)

- **Protocol for joining:** after connecting, the client must send `JOIN_GAME` with an optional `playerName`. To create a private game it adds `createRoom: true` (the server answers `ROOM_CREATED {roomCode}`); to join one it adds `roomCode`. Connections that don't join within 15s are closed. Before the countdown, both players get `GAME_STATE READY` with `yourName`/`opponentName`. `GAME_OVER` carries `yourStats` (including every answer with its timing) and `opponentStats` (totals only).
- **`websocket/GameWebSocketHandler`** is the core of the game (~800 lines). It is a `TextWebSocketHandler` registered at `/game` in `WebSocketConfig`, which reads its allowed origins from `app.allowed-origins`. It owns:
  - the game flow: countdown → start → per-player question delivery → score updates → timer-driven `endGame` → game over / rematch / forfeit
  - a shared `ScheduledExecutorService` for all delayed work; never block with `Thread.sleep`
  - rate limiting and heartbeat-based timeout detection
- **`service/GameService`** handles matchmaking and per-game state changes. Public matchmaking uses a single `waitingGame` slot. Private games live in `privateRooms` by a 6-character code (no look-alike characters); a code works once and dies with its host. Nicknames go through `PlayerNames.sanitize`, duplicate names in one game get a " (2)" suffix, and the winner is always decided by score, never by name. Player identity is the WebSocket session ID. `joinGame`, `removePlayer` and `requestRematch` are `synchronized`. Watch for race conditions around game start and rematch.
- **`model/GameSession`** is a state machine `WAITING → READY → RUNNING → FINISHED` (rematch resets `FINISHED → READY`). The clock starts only on `READY → RUNNING`, which happens after the countdown in `actuallyStartGame`. `endGame()` returns true only for the call that actually ends the game, and it cancels every task registered with `addScheduledTask` (countdown, game timer, connection monitor). Route every way a game can end through it so stale timers can't end a later round. Questions live on each `Player`, not on the session.
- **Concurrency:** every session is wrapped in a `ConcurrentWebSocketSessionDecorator` (looked up by ID in `sendMessage`), answers are processed under `synchronized(player)`, and rematch requests go through `game.getRematchLock()`.
- **`dto/`** holds the WebSocket protocol. `BaseMessage` uses Jackson polymorphism on the `type` field (`@JsonSubTypes`). To add a message type: create the DTO, register it in `BaseMessage`, add a `case` in `GameWebSocketHandler.handleTextMessage` (client→server), and/or add a handler in `messageHandlers.js` (server→client). The server sends `GAME_STATE` (RUNNING) when a round starts, which the client needs for Forfeit. `JoinGameMessage` isn't used yet; it's kept for nicknames.
- **`config/GameConfiguration`** binds the `game.*` properties.
- **`SecurityConfig`** permits all requests on purpose (anonymous play). The default user auto-configuration is excluded in `WarDuelApplication`. JPA + H2 (local) / Postgres (production) are kept for the planned leaderboard.
- **`HomeController`** serves the Thymeleaf templates `index` (the SPA), `impressum` and `datenschutz`. It passes `questionsPerGame`, `durationSeconds` and `winScore` to the page, which shows them and exposes them as `<body data-*>` for the JS. Never hardcode those numbers in the frontend.

### Frontend (`src/main/resources/`)

- README screenshots live in `docs/screenshots/`. Retake them when the UI changes visibly.
- `templates/index.html` is the single page. Its four `.screen` sections (lobby, waiting/countdown, game, result) are switched with the `hidden` attribute through `ui.showScreen()`, so only one is visible at a time. `#toast` shows messages on top of any screen. It loads `static/js/game-modular.js` as an ES module.
- Data flow in `static/js/modules/`:
  - `websocket.js`: connection, heartbeat, reload on loss. Events from a replaced socket are ignored.
  - `messageHandlers.js`: routes by `type`. It holds the ✓/✗ answer mark for `FEEDBACK_HOLD_MS` before the next question replaces it. In a `SCORE_UPDATE`, `wasCorrect` is `null` when the update is about the opponent's answer.
  - `gameState.js` (state, settings, client timer) and `uiController.js` (DOM only, no logic; also draws the tally-mark scores as SVG).
  - `gameActions.js`: play, cancel, give up (needs a second tap), rematch, find a new opponent, back to the main menu. `inputHandler.js`: number pad plus keyboard (digits, Backspace, `-`, Enter, Escape). `domElements.js`: cached element lookups.
- **Design: "Chalkboard duel".** All tokens are CSS variables at the top of `static/css/style.css`, the only stylesheet:
  - colors: board `#263d33`, chalk `#eef0e8`, you = yellow `#f2d45c`, opponent = pink `#f3a1b5`, correct = green `#a8e6a1`, wood tray
  - fonts: Cabin Sketch for big chalk text (titles, question, numbers on keys) and Lexend for everything else, including filled buttons, where the hatched chalk face loses contrast
  - fonts are **self-hosted** in `static/fonts/` with their OFL licenses. Don't load anything from Google or other CDNs: that's a GDPR problem in Germany, and the privacy policy doesn't cover it.
  - phone-first: the board fills the screen and the wooden `.tray` holds the legal links. From 820px up, the board is framed on a wall and the game switches to two columns.

## Roadmap

Work happens on branch `refresh`, one commit per phase. Phases 1 (cleanup), 2 (backend bugs), 3 (tests, CI, JDK 25 support) and 4 (chalkboard redesign) are done.

5. **Features and game feel:**
   - nicknames, a private match link, post-game stats
   - challenge types: Classic, True/False Blitz, Missing Number, Compare
   - a leaderboard ranked by total wins; identity is a nickname plus a secret device token, and only public matchmaking games count
   - game feel in the chalkboard style: a match intro, streaks and combos, chalk-dust effects on correct answers, a celebration on wins
   - Web Audio sound effects (chalk scratches, taps, countdown, win/lose) with a mute button
6. **Hosting:** a free Render web service (`Dockerfile` + `render.yaml`) with Postgres on Neon. No custom domain is needed.

## Conventions

- Many existing code comments and log messages are in German. Write new ones in English.
- Verify UI changes in a real browser, including states only reachable by interacting (countdown, wrong and correct answers, result variants, rematch, opponent leaving), at desktop and phone widths.
