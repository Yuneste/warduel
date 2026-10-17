# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

MathWars (repo/artifact name `warduel`): a real-time 1v1 math quiz game. Spring Boot 3.2 / Java 17 backend talking to a vanilla ES6-module frontend over a raw WebSocket. The frontend is served as static resources.

## Game requirements (source of truth)

- Two players are matched automatically: the first to connect waits, and the second fills the game.
- Each player gets their **own** 20 random questions (add/subtract/multiply/divide, configurable via `game.*`). Subtraction never goes negative, and division always comes out even.
- 60-second round with a 3-second countdown before it. The first player to reach 20 correct answers wins immediately. Otherwise the higher score wins when time runs out, and equal scores are a draw.
- Live score updates go to both players after every answer. Each question can be answered only once, and answers after the time is up are rejected.
- After the game, both players must agree to a rematch, which starts without a countdown. Forfeiting or disconnecting during a game gives the opponent the win. Leaving during the queue or countdown cancels the match.
- Mobile stability: the client sends a `HEARTBEAT` every 5s, and the server closes connections that have been silent for more than 10s. Limit: 10 messages/second per player.
- The UI is English. The Impressum and Datenschutz pages stay German (legally required).

## Commands

Use **JDK 17–21**. On JDK 23+, the Lombok version pinned by Spring Boot 3.2 fails to compile (`cannot find symbol` in DTOs). Locally, JDK 21 is at `C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot`.

```bash
./mvnw spring-boot:run                       # http://localhost:8080 (open two tabs to play)
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.port=8090 --app.allowed-origins=http://localhost:8090"
./mvnw clean package                         # target/warduel-0.0.1-SNAPSHOT.jar
./mvnw test                                  # run tests
./mvnw test -Dtest=ClassName#methodName      # single test
```

`src/test/` is currently **gitignored**: the tests exist locally but are stale and are due to be rewritten and tracked again. When changing ports, also set `app.allowed-origins`, or the WebSocket handshake is rejected with 403. There's no JS build step or linter: edit files under `src/main/resources/static/` and reload.

## Architecture

### Backend (`src/main/java/com/warduel/warduel/`)

- **`websocket/GameWebSocketHandler`** is the core of the game (~800 lines). It is a `TextWebSocketHandler` registered at `/game` in `WebSocketConfig`, which reads its allowed origins from `app.allowed-origins`. It owns:
  - the game flow: countdown → start → per-player question delivery → score updates → timer-driven `endGame` → game over / rematch / forfeit
  - a shared `ScheduledExecutorService` for all delayed work; never block with `Thread.sleep`
  - rate limiting and heartbeat-based timeout detection
- **`service/GameService`** handles matchmaking and per-game state changes. Matchmaking happens **on WebSocket connect** (`afterConnectionEstablished` → `joinGame`) through a single `waitingGame` slot, so the client never sends `JOIN_GAME`. Player identity is the WebSocket session ID. `joinGame`, `removePlayer` and `requestRematch` are `synchronized`. Watch for race conditions around game start and rematch.
- **`model/GameSession`** is a state machine `WAITING → READY → RUNNING → FINISHED` (rematch resets `FINISHED → READY`). The clock starts only on `READY → RUNNING`, which happens after the countdown in `actuallyStartGame`. `endGame()` returns true only for the call that actually ends the game, and it cancels every task registered with `addScheduledTask` (countdown, game timer, connection monitor). Route every way a game can end through it so stale timers can't end a later round. Questions live on each `Player`, not on the session.
- **Concurrency:** every session is wrapped in a `ConcurrentWebSocketSessionDecorator` (looked up by ID in `sendMessage`), answers are processed under `synchronized(player)`, and rematch requests go through `game.getRematchLock()`.
- **`dto/`** holds the WebSocket protocol. `BaseMessage` uses Jackson polymorphism on the `type` field (`@JsonSubTypes`). To add a message type: create the DTO, register it in `BaseMessage`, add a `case` in `GameWebSocketHandler.handleTextMessage` (client→server), and/or add a handler in `messageHandlers.js` (server→client). The server sends `GAME_STATE` (RUNNING) when a round starts, which the client needs for Forfeit. `JoinGameMessage` isn't used yet; it's kept for nicknames.
- **`config/GameConfiguration`** binds the `game.*` properties.
- **`SecurityConfig`** permits all requests on purpose (anonymous play). The default user auto-configuration is excluded in `WarDuelApplication`. JPA + H2 (local) / Postgres (production) are kept for the planned leaderboard.
- **`HomeController`** serves the Thymeleaf templates `index` (the SPA), `impressum` and `datenschutz`.

### Frontend (`src/main/resources/`)

- `templates/index.html` is the single page with every screen (lobby, waiting/countdown, game, result). It loads `static/js/game-modular.js` as an ES module.
- Data flow in `static/js/modules/`: `websocket.js` (connection, heartbeat, reload on loss) → `messageHandlers.js` (routes by `type`) → `gameState.js` (state + client timer) and `uiController.js` (DOM only, no logic). `gameActions.js` holds the high-level actions (play, leave, rematch), `inputHandler.js` the on-screen number pad, and `domElements.js` the cached element lookups.
- `static/css/style.css` is the only stylesheet.

## Roadmap

Work happens on branch `refresh`, one commit per phase. Phase 1 (cleanup) and Phase 2 (backend bugs) are done.

3. **Tests and CI:** rewrite and track `src/test`, add WebSocket integration tests that play real matches, add GitHub Actions with a badge, upgrade dependencies so JDK 25 works.
4. **Complete visual redesign:** the current pastel design is to be replaced, not polished. Use the `frontend-design` and `ui-ux-pro-max` skills, offer 2–3 directions to choose from, then rebuild every screen phone-first. Include keyboard input, values read from the server, visible rematch and error states (for example "Opponent left the queue" is currently invisible on the waiting screen), and accessibility.
5. **Features and game feel:**
   - nicknames, a private match link, post-game stats
   - challenge types: Classic, True/False Blitz, Missing Number, Compare
   - a leaderboard ranked by total wins; identity is a nickname plus a secret device token, and only public matchmaking games count
   - a versus intro, tug-of-war bar, combos and confetti
   - Web Audio sound effects with a mute button
6. **Hosting:** a free Render web service (`Dockerfile` + `render.yaml`) with Postgres on Neon. No custom domain is needed.

## Conventions

- Many existing code comments and log messages are in German. Write new ones in English.
- Verify UI changes in a real browser, including states only reachable by interacting (countdown, wrong and correct answers, result variants, rematch, opponent leaving), at desktop and phone widths.
